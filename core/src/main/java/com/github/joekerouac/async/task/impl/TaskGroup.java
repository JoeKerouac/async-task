/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
 * to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.github.joekerouac.async.task.impl;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import com.github.joekerouac.async.task.Const;
import com.github.joekerouac.async.task.entity.AsyncTask;
import com.github.joekerouac.async.task.model.AsyncTaskProcessorEngineConfig;
import com.github.joekerouac.async.task.model.ExecStatus;
import com.github.joekerouac.async.task.model.TaskFinishCode;
import com.github.joekerouac.async.task.model.TaskGroupConfig;
import com.github.joekerouac.async.task.model.TaskQueueConfig;
import com.github.joekerouac.async.task.service.InternalTraceService;
import com.github.joekerouac.async.task.spi.AbstractAsyncTaskProcessor;
import com.github.joekerouac.async.task.spi.AsyncTaskProcessorEngine;
import com.github.joekerouac.async.task.spi.AsyncTaskRepository;
import com.github.joekerouac.async.task.spi.AsyncTransactionManager;
import com.github.joekerouac.async.task.spi.MonitorService;
import com.github.joekerouac.async.task.spi.ProcessorRegistry;
import com.github.joekerouac.async.task.spi.TaskCacheQueue;
import com.github.joekerouac.common.tools.collection.CollectionUtil;
import com.github.joekerouac.common.tools.constant.StringConst;

import lombok.CustomLog;

/**
 * @author JoeKerouac
 * @date 2023-11-11 11:45
 * @since 4.0.0
 */
@CustomLog
public class TaskGroup {

    private final TaskGroupConfig config;

    @NotNull
    private final AsyncTaskRepository repository;

    @NotNull
    private final AsyncTransactionManager transactionManager;

    private volatile AsyncTaskProcessorEngine engine;

    private volatile TaskCacheQueue taskCacheQueue;

    private volatile boolean start;

    public TaskGroup(TaskGroupConfig config) {
        this.config = config;
        this.start = false;
        this.repository = config.getRepository();
        this.transactionManager = config.getTransactionManager();
    }

    protected AsyncTaskProcessorEngine build(TaskGroupConfig taskGroupConfig, TaskCacheQueue taskCacheQueue) {
        AsyncTaskProcessorEngineConfig asyncTaskProcessorEngineConfig = new AsyncTaskProcessorEngineConfig();
        asyncTaskProcessorEngineConfig.setAsyncThreadPoolConfig(taskGroupConfig.getThreadPoolConfig());
        asyncTaskProcessorEngineConfig.setProcessorRegistry(taskGroupConfig.getProcessorRegistry());
        asyncTaskProcessorEngineConfig.setTaskCacheQueue(taskCacheQueue);
        asyncTaskProcessorEngineConfig.setTraceService(taskGroupConfig.getTraceService());
        asyncTaskProcessorEngineConfig.setMonitorService(taskGroupConfig.getMonitorService());
        asyncTaskProcessorEngineConfig.setRepository(taskGroupConfig.getRepository());
        asyncTaskProcessorEngineConfig.setInternalTraceService(taskGroupConfig.getInternalTraceService());
        return config.getEngineFactory().create(asyncTaskProcessorEngineConfig);
    }

    /**
     * 添加任务
     *
     * @param task
     *            任务
     * @return true表示保存成功，false表示主键冲突，保存失败
     */
    public boolean addTask(@NotNull AsyncTask task) {
        boolean result = config.getRepository().save(task);
        if (result) {
            transactionManager.runAfterCommit(() -> {
                taskCacheQueue.addTask(task);
                LOGGER.info("任务添加, 将任务[{}]添加到内存队列中", task);
            });
        }
        return result;
    }

    /**
     * 将任务从内存中移除
     *
     * @param taskRequestIds
     *            任务
     */
    public void removeTask(Set<String> taskRequestIds) {
        transactionManager.runAfterCommit(() -> taskCacheQueue.removeTask(taskRequestIds));
    }

    /**
     * 唤醒任务，如果任务处于{@link ExecStatus#WAIT}状态，则任务被唤醒，切换到{@link ExecStatus#READY}状态
     *
     * @param asyncTask
     *            要唤醒的任务
     * @return true表示通知成功，false表示通知失败，可能是任务不存在或者当前任务状态已经变化
     */
    public boolean notifyTask(AsyncTask asyncTask) {
        String ip = Const.IP + StringConst.DOT + config.getInternalTraceService().generate();
        boolean result = repository.casUpdate(asyncTask.getRequestId(), ExecStatus.WAIT, ExecStatus.READY,
            asyncTask.getExecIp(), ip) > 0;
        if (result) {
            transactionManager.runAfterCommit(() -> {
                // 这里使用for update，防止查询到的数据是从库的未及时更新的，不过我们这里没有事务，所以实际上并不会锁定
                AsyncTask task = repository.selectForUpdate(asyncTask.getRequestId());
                taskCacheQueue.addTask(task);
                LOGGER.info("任务通知, 将任务[{}]添加到内存队列中", task);
            });
        } else {
            LOGGER.info("任务通知失败: [{}]", asyncTask.getRequestId());
        }
        return result;
    }

    /**
     * 唤醒任务，切换到{@link ExecStatus#READY}状态；注意，要更新的任务调用方必须锁定，并且判断状态，内部不做任何判断
     *
     * @param requestIdSet
     *            任务requestId集合
     * @return 成功唤醒的任务集合，其他可能因为任务不存在或者任务不是wait状态导致唤醒失败
     */
    public Set<String> notifyTask(Set<String> requestIdSet) {
        if (requestIdSet.isEmpty()) {
            return Collections.emptySet();
        }

        String ip = Const.IP + StringConst.DOT + config.getInternalTraceService().generate();
        repository.batchUpdate(requestIdSet, ExecStatus.READY, TaskFinishCode.NONE, ip);

        transactionManager.runAfterCommit(() -> {
            // 这里使用for update，防止查询到的数据是从库的未及时更新的，不过我们这里没有事务，所以实际上并不会锁定
            List<AsyncTask> asyncTasks = repository.selectForUpdate(requestIdSet);
            asyncTasks.forEach(taskCacheQueue::addTask);
            LOGGER.info("任务通知, 将任务[{}]添加到内存队列中", requestIdSet);
        });

        return requestIdSet;
    }

    /**
     * 取消任务，切换到{@link ExecStatus#FINISH}状态；注意，要更新的任务调用方必须判断状态，内部不做任何判断
     *
     * @param task
     *            要取消的任务
     */
    public boolean cancelTask(AsyncTask task) {
        String ip = Const.IP + StringConst.DOT + config.getInternalTraceService().generate();
        boolean result = repository.casCancel(task.getRequestId(), task.getStatus(), ip) > 0;

        if (result) {
            transactionManager.runAfterCommit(() -> {
                removeTask(Collections.singleton(task.getRequestId()));
                // 这里使用for update，防止查询到的数据是从库的未及时更新的，不过我们这里没有事务，所以实际上并不会锁定
                LOGGER.info("任务取消, 从队列移除, [{}]", task.getRequestId());
            });
        } else {
            LOGGER.info("任务取消失败，当前任务: [{}]", task.getRequestId());
        }
        return result;
    }

    /**
     * 取消任务，切换到{@link ExecStatus#FINISH}状态；注意，要更新的任务调用方必须锁定，并且判断状态，内部不做任何判断
     *
     * @param requestIdSet
     *            任务requestId集合
     */
    public void cancelTask(Set<String> requestIdSet) {
        if (requestIdSet.isEmpty()) {
            return;
        }

        String ip = Const.IP + StringConst.DOT + config.getInternalTraceService().generate();
        repository.batchUpdate(requestIdSet, ExecStatus.FINISH, TaskFinishCode.CANCEL, ip);

        transactionManager.runAfterCommit(() -> {
            removeTask(requestIdSet);
            // 这里使用for update，防止查询到的数据是从库的未及时更新的，不过我们这里没有事务，所以实际上并不会锁定
            LOGGER.info("任务取消, 从队列移除, [{}]", requestIdSet);
        });
    }

    /**
     * 启动任务组
     */
    public synchronized void start() {
        if (start) {
            return;
        }
        start = true;
        // 注意启动顺序，必须是先启动队列，然后启动线程池
        taskCacheQueue = config.getTaskCacheQueueFactory().build(config.getTaskQueueConfig(), repository);
        taskCacheQueue.start();
        engine = build(config, taskCacheQueue);
        engine.start();

        ProcessorRegistry processorRegistry = config.getProcessorRegistry();
        processorRegistry.addListener(new ProcessorRegistry.TaskProcessorListener() {

            private void refreshTaskTypes() {
                Set<String> allTaskType = processorRegistry.getAllTaskType();
                if (CollectionUtil.isEmpty(allTaskType)) {
                    taskCacheQueue.refreshTaskTypes(Collections.emptySet());
                    return;
                }

                Set<String> taskTypeGroup = allTaskType.stream().filter(taskType -> {
                    TaskQueueConfig taskQueueConfig = config.getTaskQueueConfig();
                    Set<String> set = taskQueueConfig.getTaskTypeGroup();
                    return taskQueueConfig.isContain() == set.contains(taskType);
                }).collect(Collectors.toSet());
                taskCacheQueue.refreshTaskTypes(taskTypeGroup);
            }

            @Override
            public void onRegister(String taskType, AbstractAsyncTaskProcessor<?> oldProcessor,
                AbstractAsyncTaskProcessor<?> newProcessor) {
                refreshTaskTypes();
            }

            @Override
            public void onRemove(String taskType, AbstractAsyncTaskProcessor<?> processor) {
                refreshTaskTypes();
            }
        });

        Thread monitorThread = new Thread(() -> {
            MonitorService monitorService = config.getMonitorService();
            InternalTraceService internalTraceService = config.getInternalTraceService();

            while (start) {
                try {
                    Thread.sleep(config.getMonitorInterval());
                    if (!start) {
                        return;
                    }
                    // 统计在指定时间之前就开始执行的任务
                    LocalDateTime execTime = LocalDateTime.now().plus(-config.getExecTimeout(), ChronoUnit.MILLIS);
                    List<AsyncTask> tasks = repository.stat(execTime);
                    for (AsyncTask task : tasks) {
                        // 查找任务处理器
                        AbstractAsyncTaskProcessor<Object> processor =
                            config.getProcessorRegistry().getProcessor(task.getProcessor());
                        if (processor == null) {
                            // 理论上不可能
                            continue;
                        }
                        String requestId = task.getRequestId();
                        Map<String, Object> cache = new HashMap<>();
                        // 解析数据
                        Object context;
                        try {
                            context = processor.deserialize(requestId, task.getTask(), cache);
                        } catch (Throwable throwable) {
                            continue;
                        }

                        // 判断执行超时是否可以直接重试
                        if (!processor.canReExec(requestId, context)) {
                            continue;
                        }
                        // 任务更新为ready重新执行，这里不关心是否设置成功，失败了后续还会轮询到
                        String ip = Const.IP + StringConst.DOT + internalTraceService.generate();
                        repository.casUpdate(requestId, ExecStatus.RUNNING, ExecStatus.READY, task.getExecIp(), ip);
                        monitorService.taskReExec(task);
                    }
                    if (!tasks.isEmpty()) {
                        monitorService.taskExecTimeout(tasks, config.getExecTimeout());
                    }
                } catch (Throwable throwable) {
                    if (!(throwable instanceof InterruptedException)) {
                        LOGGER.info(throwable, "监听线程异常");
                    }
                }
            }
        }, "异步任务执行超时监控");
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    /**
     * 停止任务组
     */
    public synchronized void stop() {
        if (!start) {
            return;
        }
        start = false;
        // 注意，队列必须先于执行线程池关闭
        taskCacheQueue.stop();
        engine.stop();
    }

}
