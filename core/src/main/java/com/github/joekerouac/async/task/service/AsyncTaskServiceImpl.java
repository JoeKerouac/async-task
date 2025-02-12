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
package com.github.joekerouac.async.task.service;

import com.github.joekerouac.async.task.AsyncTaskService;
import com.github.joekerouac.async.task.Const;
import com.github.joekerouac.async.task.entity.AsyncTask;
import com.github.joekerouac.async.task.entity.common.ExtMap;
import com.github.joekerouac.async.task.impl.MonitorServiceAdaptor;
import com.github.joekerouac.async.task.impl.MonitorServiceProxy;
import com.github.joekerouac.async.task.impl.TaskGroup;
import com.github.joekerouac.async.task.model.AsyncServiceConfig;
import com.github.joekerouac.async.task.model.AsyncTaskExecutorConfig;
import com.github.joekerouac.async.task.model.CancelStatus;
import com.github.joekerouac.async.task.model.ExecStatus;
import com.github.joekerouac.async.task.model.TaskFinishCode;
import com.github.joekerouac.async.task.model.TaskGroupConfig;
import com.github.joekerouac.async.task.model.TaskQueueConfig;
import com.github.joekerouac.async.task.model.TransStrategy;
import com.github.joekerouac.async.task.spi.AbstractAsyncTaskProcessor;
import com.github.joekerouac.async.task.spi.AsyncTaskRepository;
import com.github.joekerouac.async.task.spi.IDGenerator;
import com.github.joekerouac.async.task.spi.MonitorService;
import com.github.joekerouac.async.task.spi.ProcessorRegistry;
import com.github.joekerouac.async.task.spi.TraceService;
import com.github.joekerouac.common.tools.collection.CollectionUtil;
import com.github.joekerouac.common.tools.constant.ExceptionProviderConst;
import com.github.joekerouac.common.tools.string.StringUtils;
import com.github.joekerouac.common.tools.util.Assert;
import lombok.CustomLog;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
@CustomLog
public class AsyncTaskServiceImpl implements AsyncTaskService {

    /**
     * 异步任务配置
     */
    private final AsyncServiceConfig config;

    /**
     * 异步任务执行引擎
     */
    private final TaskGroup defaultGroup;

    /**
     * key是processor name
     */
    private final Map<String, TaskGroup> taskGroupMap;

    /**
     * 当前任务是否启动
     */
    private volatile boolean start = false;

    private final ProcessorRegistry processorRegistry;

    public AsyncTaskServiceImpl(@NotNull AsyncServiceConfig config) {
        Assert.notNull(config, "config不能为null", ExceptionProviderConst.IllegalArgumentExceptionProvider);
        Const.VALIDATION_SERVICE.validate(config);

        processorRegistry = config.getProcessorRegistry();

        MonitorService monitorService = config.getMonitorService();
        monitorService = monitorService == null ? new MonitorServiceAdaptor() : monitorService;
        if (!(monitorService instanceof MonitorServiceProxy)) {
            monitorService = new MonitorServiceProxy(monitorService);
        }
        config.setMonitorService(monitorService);
        if (config.getEngineFactory() == null) {
            config.setEngineFactory(new DefaultAsyncTaskProcessorEngineFactory());
        }

        this.taskGroupMap = new HashMap<>();
        this.config = config;

        InternalTraceService internalTraceService = new InternalTraceService();
        Map<Set<String>, AsyncTaskExecutorConfig> executorConfigs = config.getExecutorConfigs();
        Set<String> set = new HashSet<>();
        if (!CollectionUtil.isEmpty(executorConfigs)) {
            executorConfigs.forEach((processorNames, executorConfig) -> {
                TaskGroup taskGroup = build(config, executorConfig, processorNames, true, internalTraceService);
                for (String processorName : processorNames) {
                    Assert.assertTrue(set.add(processorName),
                        StringUtils.format("处理器有多个配置, processor: [{}]", processorName),
                        ExceptionProviderConst.IllegalArgumentExceptionProvider);
                    taskGroupMap.put(processorName, taskGroup);
                }
            });
        }

        this.defaultGroup = build(config, config.getDefaultExecutorConfig(), set, false, internalTraceService);

        TaskClearRunner taskClearRunner = new TaskClearRunner(config.getRepository(), config.getProcessorRegistry());
        Thread taskClearThread = new Thread(taskClearRunner, "异步任务自动清理线程");
        taskClearThread.setPriority(Thread.MIN_PRIORITY);
        taskClearThread.setDaemon(true);
        taskClearThread.start();
    }

    /**
     * 构建任务组
     * 
     * @param asyncServiceConfig
     *            异步任务全局配置
     * @param executorConfig
     *            执行器配置
     * @param taskTypeGroup
     *            任务列表
     * @param contain
     *            true表示异步任务引擎只处理processorGroup中包含的任务，false表示异步任务处理引擎不应该处理processorGroup中包含的任务，而应该处理所有其他任务
     * @param internalTraceService
     *            内部trace服务
     * @return 任务组
     */
    private TaskGroup build(AsyncServiceConfig asyncServiceConfig, AsyncTaskExecutorConfig executorConfig,
        Set<String> taskTypeGroup, boolean contain, InternalTraceService internalTraceService) {
        int cacheQueueSize = executorConfig.getCacheQueueSize();
        int loadThreshold = executorConfig.getLoadThreshold();
        Assert.assertTrue(
            loadThreshold < cacheQueueSize || (loadThreshold == 0 && cacheQueueSize == 0), StringUtils
                .format("触发捞取任务的队列长度阈值应该小于缓存队列的长度，当前触发捞取任务的队列长度为：[{}],当前缓存队列长度为：[{}]", loadThreshold, cacheQueueSize),
            ExceptionProviderConst.IllegalArgumentExceptionProvider);

        TaskQueueConfig taskQueueConfig = new TaskQueueConfig();
        taskQueueConfig.setLoadInterval(executorConfig.getLoadInterval());
        taskQueueConfig.setCacheQueueSize(executorConfig.getCacheQueueSize());
        taskQueueConfig.setLoadThreshold(executorConfig.getLoadThreshold());
        taskQueueConfig.setLoadTaskFromRepository(executorConfig.isLoadTaskFromRepository());
        taskQueueConfig.setTaskTypeGroup(taskTypeGroup);
        taskQueueConfig.setContain(contain);

        TaskGroupConfig taskGroupConfig = new TaskGroupConfig();
        taskGroupConfig.setTaskCacheQueueFactory(asyncServiceConfig.getTaskCacheQueueFactory());
        taskGroupConfig.setEngineFactory(asyncServiceConfig.getEngineFactory());
        taskGroupConfig.setTaskQueueConfig(taskQueueConfig);
        taskGroupConfig.setThreadPoolConfig(executorConfig.getThreadPoolConfig());
        taskGroupConfig.setProcessorRegistry(asyncServiceConfig.getProcessorRegistry());
        taskGroupConfig.setTraceService(asyncServiceConfig.getTraceService());
        taskGroupConfig.setMonitorService(asyncServiceConfig.getMonitorService());
        taskGroupConfig.setExecTimeout(executorConfig.getExecTimeout());
        taskGroupConfig.setMonitorInterval(executorConfig.getMonitorInterval());
        taskGroupConfig.setRepository(asyncServiceConfig.getRepository());
        taskGroupConfig.setTransactionManager(asyncServiceConfig.getTransactionManager());
        taskGroupConfig.setInternalTraceService(internalTraceService);

        return new TaskGroup(taskGroupConfig);
    }

    @Override
    public void start() {
        synchronized (config) {
            if (start) {
                LOGGER.warn("当前异步任务服务已经启动，请勿重复调用启动方法");
            } else {
                defaultGroup.start();
                if (!taskGroupMap.isEmpty()) {
                    taskGroupMap.values().forEach(TaskGroup::start);
                }
                start = true;
            }
        }
    }

    @Override
    public void stop() {
        synchronized (config) {
            if (start) {
                LOGGER.info("异步任务引擎准备关闭...");
                defaultGroup.stop();
                if (!taskGroupMap.isEmpty()) {
                    taskGroupMap.values().forEach(TaskGroup::stop);
                }
                start = false;
                LOGGER.info("异步任务引擎关闭成功...");
            } else {
                LOGGER.warn("当前异步任务服务已经关闭，请勿重复调用关闭方法");
            }
        }
    }

    @Override
    public void addProcessor(final AbstractAsyncTaskProcessor<?> processor) {
        for (String taskType : processor.processors()) {
            AbstractAsyncTaskProcessor<?> old = processorRegistry.registerProcessor(taskType, processor);
            Assert.isNull(old,
                StringUtils.format("当前processor已经存在, taskType: [{}], old: [{}], new: [{}]", taskType,
                    old == null ? null : old.getClass(), processor.getClass()),
                ExceptionProviderConst.IllegalArgumentExceptionProvider);
        }
    }

    @Override
    public <T, P extends AbstractAsyncTaskProcessor<T>> P removeProcessor(final String processorName) {
        return processorRegistry.removeProcessor(processorName);
    }

    @Override
    public <T, P extends AbstractAsyncTaskProcessor<T>> P getProcessor(final String processorName) {
        return processorRegistry.getProcessor(processorName);
    }

    @Override
    public void addTask(final String requestId, final Object task, final int maxRetry, final LocalDateTime execTime,
        final String taskProcessor, final TransStrategy transStrategy) {
        addTaskInternal(requestId, task, maxRetry, execTime, taskProcessor, transStrategy, ExecStatus.READY);
    }

    @Override
    public void addTaskWithWait(final String requestId, final Object task, final int maxRetry,
        final LocalDateTime execTime, final String taskProcessor, final TransStrategy transStrategy) {
        addTaskInternal(requestId, task, maxRetry, execTime, taskProcessor, transStrategy, ExecStatus.WAIT);
    }

    @Override
    public boolean notifyTask(final String requestId, TransStrategy transStrategy) {
        AsyncTask task = config.getRepository().selectByRequestId(requestId);

        if (task == null) {
            // 数据库可能是读写的，这里应该能强制让查询走主库
            task = config.getTransactionManager().runWithTrans(TransStrategy.NOT_SUPPORTED,
                () -> config.getRepository().selectForUpdate(requestId));
        }

        if (task != null && task.getStatus() == ExecStatus.WAIT) {
            String processor = task.getProcessor();
            AsyncTask notifyTask = task;
            return config.getTransactionManager().runWithTrans(transStrategy,
                () -> getTaskGroup(processor).notifyTask(notifyTask));
        } else {
            LOGGER.warn("当前要唤醒的任务不存在或者状态已经变更: [{}], [{}]", requestId, task);
            return false;
        }
    }

    @Override
    public Set<String> notifyTask(Set<String> requestIdSet, TransStrategy transStrategy) {
        if (requestIdSet.isEmpty()) {
            return Collections.emptySet();
        }

        if (requestIdSet.size() == 1) {
            String requestId = requestIdSet.iterator().next();
            if (notifyTask(requestId, transStrategy)) {
                return Collections.singleton(requestId);
            } else {
                return Collections.emptySet();
            }
        }

        if (transStrategy != TransStrategy.REQUIRED && transStrategy != TransStrategy.REQUIRES_NEW) {
            throw new IllegalArgumentException(
                StringUtils.format("当前仅支持REQUIRED和REQUIRES_NEW类型的事务, 不支持{}类型的事务", transStrategy));
        }

        AsyncTaskRepository repository = config.getRepository();

        return config.getTransactionManager().runWithTrans(transStrategy, () -> {
            // 对于一次唤醒多个，我们直接开启事务，同时加锁唤醒，理论上锁不会有竞争，因为任务期望都是WAIT状态，此时不会有其他地方会尝试加锁
            List<AsyncTask> asyncTasks = repository.selectForUpdate(requestIdSet);

            Map<String, Set<String>> requestIdMap = asyncTasks.stream().filter(asyncTask -> {
                if (asyncTask.getStatus() == ExecStatus.WAIT) {
                    return true;
                } else {
                    LOGGER.info("当前任务不存在或者状态不是WAIT，忽略, [{}], [{}]", asyncTask.getRequestId(), asyncTask);
                    return false;
                }
            }).collect(Collectors.groupingBy(AsyncTask::getProcessor,
                Collectors.mapping(AsyncTask::getRequestId, Collectors.toSet())));

            Set<String> result = new HashSet<>();
            requestIdMap.forEach((processor, set) -> result.addAll(getTaskGroup(processor).notifyTask(set)));
            return result;
        });
    }

    @Override
    public CancelStatus cancelTask(String requestId, TransStrategy transStrategy) {
        Assert.assertTrue(start, "当前服务还未启动，请先启动后调用", ExceptionProviderConst.IllegalStateExceptionProvider);

        return config.getTransactionManager().runWithTrans(transStrategy, () -> {
            AsyncTask task = config.getRepository().selectByRequestId(requestId);

            if (task == null) {
                // 数据库可能是读写的，这里应该能强制让查询走主库
                task = config.getTransactionManager().runWithTrans(TransStrategy.NOT_SUPPORTED,
                    () -> config.getRepository().selectForUpdate(requestId));
            }

            if (task == null) {
                return CancelStatus.NOT_EXIST;
            }

            if (task.getStatus() == ExecStatus.RUNNING) {
                return CancelStatus.RUNNING;
            } else if (task.getStatus() == ExecStatus.FINISH) {
                return CancelStatus.FINISH;
            } else {
                if (getTaskGroup(task.getProcessor()).cancelTask(task)) {
                    return CancelStatus.SUCCESS;
                } else {
                    return CancelStatus.UNKNOWN;
                }
            }
        });
    }

    @Override
    public Map<String, CancelStatus> cancelTask(Set<String> requestIdSet, TransStrategy transStrategy) {
        if (requestIdSet.isEmpty()) {
            return Collections.emptyMap();
        }

        if (requestIdSet.size() == 1) {
            String requestId = requestIdSet.iterator().next();
            CancelStatus cancelStatus = cancelTask(requestId, transStrategy);
            return Collections.singletonMap(requestId, cancelStatus);
        }

        if (transStrategy != TransStrategy.REQUIRED && transStrategy != TransStrategy.REQUIRES_NEW) {
            throw new IllegalArgumentException(
                StringUtils.format("当前仅支持REQUIRED和REQUIRES_NEW类型的事务, 不支持{}类型的事务", transStrategy));
        }

        AsyncTaskRepository repository = config.getRepository();

        return config.getTransactionManager().runWithTrans(transStrategy, () -> {
            // 对于一次取消多个，我们直接开启事务，同时加锁取消；
            List<AsyncTask> asyncTasks = repository.selectForUpdate(requestIdSet);
            Map<String, AsyncTask> taskMap =
                asyncTasks.stream().collect(Collectors.toMap(AsyncTask::getRequestId, Function.identity()));
            Map<String, CancelStatus> result = new HashMap<>();
            Map<String, Set<String>> requestIdMap = new HashMap<>();

            for (String requestId : requestIdSet) {
                AsyncTask asyncTask = taskMap.get(requestId);
                if (asyncTask == null) {
                    result.put(requestId, CancelStatus.NOT_EXIST);
                } else if (asyncTask.getStatus() == ExecStatus.RUNNING) {
                    result.put(requestId, CancelStatus.RUNNING);
                } else if (asyncTask.getStatus() == ExecStatus.FINISH) {
                    result.put(requestId, CancelStatus.FINISH);
                } else {
                    // 因为我们加锁了，所以只要后边没有抛异常就肯定成功
                    result.put(requestId, CancelStatus.SUCCESS);
                    requestIdMap.compute(asyncTask.getProcessor(), (processor, set) -> {
                        if (set == null) {
                            set = new HashSet<>();
                        }
                        set.add(requestId);
                        return set;
                    });
                }
            }

            requestIdMap.forEach((processor, set) -> getTaskGroup(processor).cancelTask(set));
            return result;
        });
    }

    private void addTaskInternal(final String requestId, final Object task, final int maxRetry,
        final LocalDateTime execTime, final String taskProcessor, TransStrategy transStrategy, ExecStatus status) {
        Assert.assertTrue(start, "当前服务还未启动，请先启动后调用", ExceptionProviderConst.IllegalStateExceptionProvider);

        AbstractAsyncTaskProcessor<?> processor = processorRegistry.getProcessor(taskProcessor);
        Assert.notNull(processor, StringUtils.format("指定的任务处理器 [{}] 不存在", taskProcessor),
            ExceptionProviderConst.IllegalArgumentExceptionProvider);
        IDGenerator idGenerator = config.getIdGenerator();
        String id = idGenerator.generateId();
        Assert.notBlank(id, StringUtils.format("ID生成器 [{}] 生成的ID为空", idGenerator),
            ExceptionProviderConst.IllegalStateExceptionProvider);

        // 将任务序列化
        String context = processor.serialize(task);

        TaskGroup taskGroup = getTaskGroup(taskProcessor);
        AsyncTask asyncTask = new AsyncTask();
        asyncTask.setId(id);
        asyncTask.setRequestId(requestId);
        asyncTask.setTask(context);
        asyncTask.setMaxRetry(maxRetry);
        asyncTask.setExecTime(execTime);
        asyncTask.setProcessor(taskProcessor);
        asyncTask.setRetry(0);
        asyncTask.setStatus(status);
        asyncTask.setTaskFinishCode(TaskFinishCode.NONE);
        asyncTask.setCreateIp(Const.IP);
        asyncTask.setExecIp(Const.IP);
        TraceService traceService = config.getTraceService();
        if (traceService != null) {
            String traceContext = traceService.dump();
            if (traceContext != null) {
                ExtMap<String, Object> extMap = asyncTask.getExtMap();
                if (extMap == null) {
                    extMap = new ExtMap<>();
                    asyncTask.setExtMap(extMap);
                }

                extMap.put(AsyncTask.ExtMapKey.TRACE_CONTEXT, traceContext);
            }
        }

        config.getTransactionManager().runWithTrans(transStrategy, () -> {
            if (!taskGroup.addTask(asyncTask)) {
                // 主键冲突保存失败
                config.getMonitorService().duplicateTask(requestId, task);
            }
        });
    }

    private TaskGroup getTaskGroup(String processor) {
        return Optional.ofNullable(taskGroupMap.get(processor)).orElse(defaultGroup);
    }

}
