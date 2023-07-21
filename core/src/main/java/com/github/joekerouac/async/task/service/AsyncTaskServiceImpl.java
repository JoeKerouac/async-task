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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.validation.constraints.NotNull;

import com.github.joekerouac.async.task.AsyncTaskService;
import com.github.joekerouac.async.task.Const;
import com.github.joekerouac.async.task.entity.AsyncTask;
import com.github.joekerouac.async.task.entity.common.ExtMap;
import com.github.joekerouac.async.task.impl.MonitorServiceAdaptor;
import com.github.joekerouac.async.task.impl.MonitorServiceProxy;
import com.github.joekerouac.async.task.model.AsyncServiceConfig;
import com.github.joekerouac.async.task.model.AsyncTaskExecutorConfig;
import com.github.joekerouac.async.task.model.AsyncTaskProcessorEngineConfig;
import com.github.joekerouac.async.task.model.CancelStatus;
import com.github.joekerouac.async.task.model.ExecStatus;
import com.github.joekerouac.async.task.model.TaskFinishCode;
import com.github.joekerouac.async.task.model.TransStrategy;
import com.github.joekerouac.async.task.spi.AbstractAsyncTaskProcessor;
import com.github.joekerouac.async.task.spi.AsyncTaskProcessorEngine;
import com.github.joekerouac.async.task.spi.AsyncTaskRepository;
import com.github.joekerouac.async.task.spi.IDGenerator;
import com.github.joekerouac.async.task.spi.MonitorService;
import com.github.joekerouac.async.task.spi.TraceService;
import com.github.joekerouac.async.task.spi.TransactionCallback;
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
    private final AsyncTaskProcessorEngine defaultEngine;

    /**
     * key是processor name
     */
    private final Map<String, AsyncTaskProcessorEngine> engineMap;

    /**
     * 当前任务是否启动
     */
    private volatile boolean start = false;

    public AsyncTaskServiceImpl(@NotNull AsyncServiceConfig config) {
        Assert.notNull(config, "config不能为null", ExceptionProviderConst.IllegalArgumentExceptionProvider);
        Const.VALIDATION_SERVICE.validate(config);

        MonitorService monitorService = config.getMonitorService();
        monitorService = monitorService == null ? new MonitorServiceAdaptor() : monitorService;
        if (!(monitorService instanceof MonitorServiceProxy)) {
            monitorService = new MonitorServiceProxy(monitorService);
        }
        config.setMonitorService(monitorService);
        if (config.getEngineFactory() == null) {
            config.setEngineFactory(new DefaultAsyncTaskProcessorEngineFactory());
        }

        TaskClearRunner taskClearRunner = new TaskClearRunner(config.getRepository());

        this.engineMap = new HashMap<>();
        this.config = config;

        Map<Set<String>, AsyncTaskExecutorConfig> executorConfigs = config.getExecutorConfigs();
        Set<String> set = new HashSet<>();
        if (!CollectionUtil.isEmpty(executorConfigs)) {
            executorConfigs.forEach((processorNames, executorConfig) -> {
                AsyncTaskProcessorEngine engine = build(config, taskClearRunner, processorNames, true);
                for (String processorName : processorNames) {
                    Assert.assertTrue(set.add(processorName),
                        StringUtils.format("处理器有多个配置, processor: [{}]", processorName),
                        ExceptionProviderConst.IllegalArgumentExceptionProvider);
                    engineMap.put(processorName, engine);
                }
            });
        }

        this.defaultEngine = build(config, taskClearRunner, set, false);

        if (CollectionUtil.isNotEmpty(config.getProcessors())) {
            config.getProcessors().forEach(this::addProcessor);
        }

        Thread taskClearThread = new Thread(taskClearRunner, "异步任务自动清理线程");
        taskClearThread.setPriority(Thread.MIN_PRIORITY);
        taskClearThread.setDaemon(true);
        taskClearThread.start();
    }

    /**
     * 构建异步任务执行引擎
     * 
     * @param asyncServiceConfig
     *            异步任务配置
     * @param taskClearRunner
     *            任务清理器
     * @param processorGroup
     *            任务列表
     * @param contain
     *            true表示异步任务引擎只处理processorGroup中包含的任务，false表示异步任务处理引擎不应该处理processorGroup中包含的任务，而应该处理所有其他任务
     * @return 异步任务执行引擎
     */
    private AsyncTaskProcessorEngine build(AsyncServiceConfig asyncServiceConfig, TaskClearRunner taskClearRunner,
        Set<String> processorGroup, boolean contain) {
        AsyncTaskProcessorEngineConfig engineConfig = new AsyncTaskProcessorEngineConfig();
        engineConfig.setExecutorConfig(asyncServiceConfig.getDefaultExecutorConfig());
        engineConfig.setRepository(asyncServiceConfig.getRepository());
        engineConfig.setProcessorSupplier(asyncServiceConfig.getProcessorSupplier());
        engineConfig.setTraceService(asyncServiceConfig.getTraceService());
        engineConfig.setMonitorService(asyncServiceConfig.getMonitorService());
        engineConfig.setTaskClearRunner(taskClearRunner);
        engineConfig.setProcessorGroup(processorGroup);
        engineConfig.setContain(contain);

        int cacheQueueSize = engineConfig.getExecutorConfig().getCacheQueueSize();
        int loadThreshold = engineConfig.getExecutorConfig().getLoadThreshold();
        Assert.assertTrue(
            loadThreshold < cacheQueueSize || (loadThreshold == 0 && cacheQueueSize == 0), StringUtils
                .format("触发捞取任务的队列长度阈值应该小于缓存队列的长度，当前触发捞取任务的队列长度为：[{}],当前缓存队列长度为：[{}]", loadThreshold, cacheQueueSize),
            ExceptionProviderConst.IllegalArgumentExceptionProvider);

        // 这里构建出仓储服务
        return asyncServiceConfig.getEngineFactory().create(engineConfig);
    }

    @Override
    public void start() {
        synchronized (config) {
            if (start) {
                LOGGER.warn("当前异步任务服务已经启动，请勿重复调用启动方法");
            } else {
                defaultEngine.start();
                if (!engineMap.isEmpty()) {
                    engineMap.values().forEach(AsyncTaskProcessorEngine::start);
                }
                start = true;
            }
        }
    }

    @Override
    public void stop() {
        synchronized (config) {
            if (start) {
                defaultEngine.stop();
                if (!engineMap.isEmpty()) {
                    engineMap.values().forEach(AsyncTaskProcessorEngine::stop);
                }
                start = false;
            } else {
                LOGGER.warn("当前异步任务服务已经关闭，请勿重复调用关闭方法");
            }
        }
    }

    @Override
    public void addProcessor(final AbstractAsyncTaskProcessor<?> processor) {
        for (String name : processor.processors()) {
            getEngine(name).addProcessor(processor);
        }
    }

    @Override
    public <T, P extends AbstractAsyncTaskProcessor<T>> P removeProcessor(final String processorName) {
        return getEngine(processorName).removeProcessor(processorName);
    }

    @Override
    public <T, P extends AbstractAsyncTaskProcessor<T>> P getProcessor(final String processorName) {
        return getEngine(processorName).getProcessor(processorName);
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
            config.getTransactionManager().runWithTrans(transStrategy, () -> {
                if (config.getRepository().casUpdate(requestId, ExecStatus.WAIT, ExecStatus.READY, Const.IP) > 0) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("唤醒任务: [{}]", task.getRequestId());
                    }
                    task.setStatus(ExecStatus.READY);
                    // 立即添加到内存中，防止调度延迟
                    addTaskToEngineAfterTransCommit(task);
                }
            });
        }
    }

    @Override
    public CancelStatus cancelTask(String requestId, TransStrategy transStrategy) {
        Assert.assertTrue(start, "当前服务还未启动，请先启动后调用", ExceptionProviderConst.IllegalStateExceptionProvider);

        return config.getTransactionManager().runWithTrans(transStrategy, () -> {
            AsyncTask task = config.getRepository().selectByRequestId(requestId);
            if (task == null) {
                return CancelStatus.NOT_EXIST;
            }

            if (task.getStatus() == ExecStatus.RUNNING) {
                return CancelStatus.RUNNING;
            } else if (task.getStatus() == ExecStatus.FINISH) {
                return CancelStatus.FINISH;
            } else {
                // cas取消成功就返回，否则继续循环
                if (config.getRepository().casCancel(requestId, task.getStatus(), Const.IP) > 0) {
                    return CancelStatus.SUCCESS;
                } else {
                    LOGGER.info("任务取消失败，当前任务状态: [{}:{}]", requestId, task.getStatus());
                    return CancelStatus.UNKNOWN;
                }
            }
        });
    }

    private void addTaskInternal(final String requestId, final Object task, final int maxRetry,
        final LocalDateTime execTime, final String taskProcessor, TransStrategy transStrategy, ExecStatus status) {
        Assert.assertTrue(start, "当前服务还未启动，请先启动后调用", ExceptionProviderConst.IllegalStateExceptionProvider);

        AbstractAsyncTaskProcessor<?> processor = getEngine(taskProcessor).getProcessor(taskProcessor);
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
            if (repository.save(asyncTask)) {
                addTaskToEngineAfterTransCommit(asyncTask);
            } else {
                // 主键冲突保存失败
                config.getMonitorService().duplicateTask(requestId, task);
            }
        });
    }

    /**
     * 将任务在事务提交后放入处理引擎中处理
     * 
     * @param asyncTask
     *            待添加的任务
     */
    private void addTaskToEngineAfterTransCommit(AsyncTask asyncTask) {
        Runnable callback = () -> {
            getEngine(asyncTask.getProcessor()).addTask(Collections.singletonList(asyncTask));
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("将任务[{}]添加到内存队列中", asyncTask);
            }
        };

        // 如果当前没有事务，直接执行回调就行了
        if (!config.getTransactionManager().isActualTransactionActive()) {
            LOGGER.debug("当前不在事务中，直接将任务提交到任务执行引擎");
            callback.run();
            return;
        } else {
            LOGGER.debug("当前在事务中，等待事务提交后将任务提交到任务执行引擎");
            config.getTransactionManager().registerCallback(new TransactionCallback() {
                @Override
                public void afterCommit() throws RuntimeException {
                    callback.run();
                }
            });
        }
    }

    private AsyncTaskProcessorEngine getEngine(String processor) {
        return Optional.ofNullable(engineMap.get(processor)).orElse(defaultEngine);
    }

}
