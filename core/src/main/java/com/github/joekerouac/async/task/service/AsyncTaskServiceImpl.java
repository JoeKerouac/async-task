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

import java.time.LocalDateTime;
import java.util.Collections;

import javax.validation.constraints.NotNull;

import com.github.joekerouac.async.task.AsyncTaskService;
import com.github.joekerouac.async.task.Const;
import com.github.joekerouac.async.task.db.TransUtil;
import com.github.joekerouac.async.task.entity.AsyncTask;
import com.github.joekerouac.async.task.impl.AsyncTaskRepositoryImpl;
import com.github.joekerouac.async.task.impl.MonitorServiceAdaptor;
import com.github.joekerouac.async.task.impl.MonitorServiceProxy;
import com.github.joekerouac.async.task.model.*;
import com.github.joekerouac.async.task.spi.*;
import com.github.joekerouac.common.tools.collection.CollectionUtil;
import com.github.joekerouac.common.tools.constant.ExceptionProviderConst;
import com.github.joekerouac.common.tools.string.StringUtils;
import com.github.joekerouac.common.tools.util.Assert;

import lombok.CustomLog;

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
    private final AsyncTaskProcessorEngine engine;

    /**
     * 当前任务是否启动
     */
    private volatile boolean start = false;

    /**
     * 任务清理线程
     */
    private final TaskClearRunner taskClearRunner;

    public AsyncTaskServiceImpl(@NotNull AsyncServiceConfig config) {
        Assert.notNull(config, "config不能为null", ExceptionProviderConst.IllegalArgumentExceptionProvider);
        Assert.assertTrue(config.getRepository() != null || config.getConnectionSelector() != null,
            "仓储服务repository和链接选择器connectionSelector不能同时为空", ExceptionProviderConst.IllegalArgumentExceptionProvider);
        Const.VALIDATION_SERVICE.validate(config);

        int cacheQueueSize = config.getCacheQueueSize();
        int loadThreshold = config.getLoadThreshold();
        Assert.assertTrue(
            loadThreshold < cacheQueueSize || (loadThreshold == 0 && cacheQueueSize == 0), StringUtils
                .format("触发捞取任务的队列长度阈值应该小于缓存队列的长度，当前触发捞取任务的队列长度为：[{}],当前缓存队列长度为：[{}]", loadThreshold, cacheQueueSize),
            ExceptionProviderConst.IllegalArgumentExceptionProvider);

        MonitorService monitorService = config.getMonitorService();
        monitorService = monitorService == null ? new MonitorServiceAdaptor() : monitorService;
        if (!(monitorService instanceof MonitorServiceProxy)) {
            monitorService = new MonitorServiceProxy(monitorService);
        }
        // 这里构建出仓储服务
        AsyncTaskRepository repository = config.getRepository();
        repository = repository != null ? repository : new AsyncTaskRepositoryImpl(config.getConnectionSelector());
        AsyncServiceConfig newConfig = new AsyncServiceConfig();
        newConfig.setRepository(repository);
        newConfig.setConnectionSelector(config.getConnectionSelector());
        newConfig.setCacheQueueSize(config.getCacheQueueSize());
        newConfig.setLoadThreshold(config.getLoadThreshold());
        newConfig.setLoadInterval(config.getLoadInterval());
        newConfig.setMonitorInterval(config.getMonitorInterval());
        newConfig.setThreadPoolConfig(config.getThreadPoolConfig());
        newConfig.setIdGenerator(config.getIdGenerator());
        newConfig.setProcessors(config.getProcessors());
        newConfig.setTransactionHook(config.getTransactionHook());
        newConfig.setMonitorService(monitorService);

        this.config = newConfig;
        this.engine = new AsyncTaskProcessorEngine(newConfig);
        this.taskClearRunner = new TaskClearRunner(config.getRepository());
        if (CollectionUtil.isNotEmpty(config.getProcessors())) {
            for (AbstractAsyncTaskProcessor<?> processor : config.getProcessors()) {
                if (processor.autoClear()) {
                    for (String processorName : processor.processors()) {
                        taskClearRunner.addClearDesc(processorName, processor.reserve());
                    }
                }
            }
        }
        Thread taskClearThread = new Thread(taskClearRunner, "异步任务自动清理线程");
        taskClearThread.setDaemon(true);
        taskClearThread.start();

    }

    @Override
    public void start() {
        synchronized (config) {
            if (start) {
                LOGGER.warn("当前异步任务服务已经启动，请勿重复调用启动方法");
            } else {
                engine.start();
                start = true;
            }
        }
    }

    @Override
    public void stop() {
        synchronized (config) {
            if (start) {
                engine.stop();
                start = false;
            } else {
                LOGGER.warn("当前异步任务服务已经关闭，请勿重复调用关闭方法");
            }
        }
    }

    @Override
    public void addProcessor(final AbstractAsyncTaskProcessor<?> processor) {
        engine.addProcessor(processor);
        if (processor.autoClear()) {
            for (String processorName : processor.processors()) {
                taskClearRunner.addClearDesc(processorName, processor.reserve());
            }
        }
    }

    @Override
    public <T, P extends AbstractAsyncTaskProcessor<T>> P removeProcessor(final String processorName) {
        P processor = engine.removeProcessor(processorName);
        if (processor.autoClear()) {
            taskClearRunner.addClearDesc(processorName, processor.reserve());
        }
        return processor;
    }

    @Override
    public <T, P extends AbstractAsyncTaskProcessor<T>> P getProcessor(final String processorName) {
        return engine.getProcessor(processorName);
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
    public void notifyTask(final String requestId, TransStrategy transStrategy) {
        AsyncTask task = config.getRepository().selectByRequestId(requestId);
        if (task != null && task.getStatus() == ExecStatus.WAIT) {
            TransUtil.run(transStrategy, () -> {
                if (config.getRepository().casUpdate(requestId, ExecStatus.WAIT, ExecStatus.READY, Const.IP) > 0) {
                    task.setStatus(ExecStatus.READY);
                    // 立即添加到内存中，防止调度延迟
                    addTaskToEngine(task, transStrategy);
                }
            });
        }
    }

    @Override
    public CancelStatus cancelTask(String requestId, TransStrategy transStrategy) {
        Assert.assertTrue(start, "当前服务还未启动，请先启动后调用", ExceptionProviderConst.IllegalStateExceptionProvider);

        return TransUtil.run(transStrategy, () -> {
            while (true) {
                AsyncTask task = config.getRepository().selectByRequestId(requestId);
                if (task != null) {
                    if (task.getStatus() == ExecStatus.RUNNING) {
                        return CancelStatus.RUNNING;
                    } else if (task.getStatus() == ExecStatus.FINISH) {
                        return CancelStatus.FINISH;
                    } else {
                        // cas取消成功就返回，否则继续循环
                        if (config.getRepository().casCancel(requestId, task.getStatus(), Const.IP) > 0) {
                            return CancelStatus.SUCCESS;
                        }
                    }
                } else {
                    return CancelStatus.NOT_EXIST;
                }
            }

        });
    }

    private void addTaskInternal(final String requestId, final Object task, final int maxRetry,
        final LocalDateTime execTime, final String taskProcessor, TransStrategy transStrategy, ExecStatus status) {
        Assert.assertTrue(start, "当前服务还未启动，请先启动后调用", ExceptionProviderConst.IllegalStateExceptionProvider);

        AbstractAsyncTaskProcessor<?> processor = engine.getProcessor(taskProcessor);
        Assert.notNull(processor, StringUtils.format("指定的任务处理器 [{}] 不存在", taskProcessor),
            ExceptionProviderConst.IllegalArgumentExceptionProvider);
        IDGenerator idGenerator = config.getIdGenerator();
        String id = idGenerator.generateId();
        Assert.notBlank(id, StringUtils.format("ID生成器 [{}] 生成的ID为空", idGenerator),
            ExceptionProviderConst.IllegalStateExceptionProvider);

        // 将任务序列化
        String context = processor.serialize(task);

        AsyncTaskRepository repository = config.getRepository();
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

        TransUtil.run(transStrategy, () -> {
            if (repository.save(asyncTask)) {
                addTaskToEngine(asyncTask, transStrategy);
            } else {
                // 主键冲突保存失败
                config.getMonitorService().duplicateTask(requestId, task);
            }
        });
    }

    /**
     * 将任务放入处理引擎中处理
     * 
     * @param asyncTask
     *            待添加的任务
     * @param strategy
     *            执行事务上下文时使用的策略
     */
    private void addTaskToEngine(AsyncTask asyncTask, TransStrategy strategy) {
        TransactionHook transactionHook = config.getTransactionHook();

        Runnable callback = () -> {
            engine.addTask(Collections.singletonList(asyncTask));
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("将任务[{}]添加到内存队列中", asyncTask);
            }
        };

        // 如果当前没有事务hook或者当前已经没有事务了，直接执行回调就行了
        if (transactionHook == null || !transactionHook.isActualTransactionActive()) {
            callback.run();
            return;
        }

        // 当前仍然在事务上下文中，那么我们就要判断当前事务上下文是否是我们执行sql时的事务上下文了，如果是，则需要等待事务结束后才能执行回调
        // 否则直接执行回调即可
        boolean needWait;

        switch (strategy) {
            case REQUIRED:
            case SUPPORTS:
                // 如果当前还有事务，说明之前就有事务，我们是加入的事务，我们需要在事务执行完毕后执行
            case MANDATORY:
                // mandatory表示当前肯定是加入事务的
                needWait = true;
                break;
            case REQUIRES_NEW:
                // 开启了新事务，此时就算有事务，也不是我们的事务了
            case NOT_SUPPORTED:
                // 以非事务的方式运行，肯定不是我们的事务
            case NEVER:
                // 以非事务的方式运行，肯定不是我们的事务
                needWait = false;
                break;
            default:
                throw new UnsupportedOperationException(StringUtils.format("不支持的事务策略：[{}]", strategy));
        }

        if (needWait) {
            transactionHook.registerCallback(new TransactionCallback() {
                @Override
                public void afterCommit() throws RuntimeException {
                    callback.run();
                }
            });
        } else {
            callback.run();
        }

    }

}
