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
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.github.joekerouac.async.task.Const;
import com.github.joekerouac.async.task.entity.AsyncTask;
import com.github.joekerouac.async.task.model.AsyncTaskProcessorEngineConfig;
import com.github.joekerouac.async.task.model.AsyncThreadPoolConfig;
import com.github.joekerouac.async.task.model.ExecResult;
import com.github.joekerouac.async.task.model.ExecStatus;
import com.github.joekerouac.async.task.model.TaskFinishCode;
import com.github.joekerouac.async.task.spi.AbstractAsyncTaskProcessor;
import com.github.joekerouac.async.task.spi.AsyncTaskProcessorEngine;
import com.github.joekerouac.async.task.spi.AsyncTaskRepository;
import com.github.joekerouac.async.task.spi.MonitorService;
import com.github.joekerouac.async.task.spi.ProcessorRegistry;
import com.github.joekerouac.async.task.spi.TaskCacheQueue;
import com.github.joekerouac.async.task.spi.TraceService;
import com.github.joekerouac.common.tools.constant.ExceptionProviderConst;
import com.github.joekerouac.common.tools.string.StringUtils;
import com.github.joekerouac.common.tools.util.Assert;

import lombok.CustomLog;

/**
 * 异步任务执行引擎
 * 
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
@CustomLog
public class DefaultAsyncTaskProcessorEngine implements AsyncTaskProcessorEngine {

    /**
     * 默认工作线程名
     */
    private static final String DEFAULT_THREAD_NAME = "async-worker";

    /**
     * 异步任务配置
     */
    private final AsyncThreadPoolConfig asyncThreadPoolConfig;

    private final ProcessorRegistry processorRegistry;

    private final TraceService traceService;

    private final TaskCacheQueue taskCacheQueue;

    private final MonitorService monitorService;

    private final AsyncTaskRepository repository;

    /**
     * 记录是否启动
     */
    private volatile boolean start = false;

    /**
     * 工作线程
     */
    private Thread[] workerThreads;

    public DefaultAsyncTaskProcessorEngine(AsyncTaskProcessorEngineConfig engineConfig) {
        Assert.notNull(engineConfig, "engineConfig不能为null", ExceptionProviderConst.IllegalArgumentExceptionProvider);
        Const.VALIDATION_SERVICE.validate(engineConfig);

        this.asyncThreadPoolConfig = engineConfig.getAsyncThreadPoolConfig();
        this.processorRegistry = engineConfig.getProcessorRegistry();
        this.traceService = engineConfig.getTraceService();
        this.taskCacheQueue = engineConfig.getTaskCacheQueue();
        this.monitorService = engineConfig.getMonitorService();
        this.repository = engineConfig.getRepository();
    }

    @Override
    public synchronized void start() {
        LOGGER.info("异步任务引擎准备启动...");
        start = true;

        workerThreads = new Thread[asyncThreadPoolConfig.getCorePoolSize()];
        // 默认使用加载本类的class loader作为线程的上下文loader
        ClassLoader loader = asyncThreadPoolConfig.getDefaultContextClassLoader() == null
            ? DefaultAsyncTaskProcessorEngine.class.getClassLoader()
            : asyncThreadPoolConfig.getDefaultContextClassLoader();

        for (int i = 0; i < workerThreads.length; i++) {
            Thread thread = new Thread(() -> {
                Thread currentThread = Thread.currentThread();
                currentThread.setContextClassLoader(loader);
                while (start) {
                    try {
                        AsyncTask task = taskCacheQueue.take();
                        if (task == null) {
                            return;
                        }

                        runTask(task);
                    } catch (Throwable throwable) {
                        if (start || !(throwable instanceof InterruptedException)) {
                            monitorService.uncaughtException(currentThread, throwable);
                        }
                    }
                }
            }, StringUtils.getOrDefault(asyncThreadPoolConfig.getThreadName(), DEFAULT_THREAD_NAME) + "-" + i);
            // 强制设置为非daemon线程
            thread.setDaemon(false);
            thread.start();

            workerThreads[i] = thread;
        }

        LOGGER.info("异步任务引擎启动成功...");
    }

    @Override
    public synchronized void stop() {
        LOGGER.info("异步任务引擎准备关闭...");
        start = false;

        // 主动将线程interrupt掉
        for (final Thread thread : workerThreads) {
            thread.interrupt();
        }

        LOGGER.info("异步任务引擎关闭成功...");
    }

    /**
     * 执行任务
     * 
     * @param task
     *            要执行的任务
     */
    protected void runTask(AsyncTask task) {
        Long t0 = System.currentTimeMillis();
        LOGGER.info("准备执行任务: [{}]", task);

        String taskRequestId = task.getRequestId();

        // 如果此时任务还不能执行，则将任务重新加到队列中
        LocalDateTime now = LocalDateTime.now();

        // 只计算到毫秒，与从内存中获取任务逻辑保持一致
        long l = ChronoUnit.MILLIS.between(now, task.getExecTime());

        if (l > 0) {
            // 理论上不会出现
            LOGGER.warn("任务 [{}] 未到执行时间，不执行，跳过执行, 当前时间：[{}]", task, now);
            // 注意，这里是专门设计为更新数据库而不把任务加入缓存的，防止任务加入队列中后立即再次到这里
            repository.update(taskRequestId, ExecStatus.READY, null, null, null, null);
            return;
        }

        // 查找任务处理器
        AbstractAsyncTaskProcessor<Object> processor = processorRegistry.getProcessor(task.getProcessor());

        String requestId = task.getRequestId();
        if (processor == null) {
            monitorService.noProcessor(requestId, task.getTask(), task.getProcessor());
            // 更新状态为没有处理器，无法处理
            repository.update(taskRequestId, ExecStatus.FINISH, TaskFinishCode.NO_PROCESSOR, null, null, Const.IP);
            return;
        }

        Map<String, Object> cache = new HashMap<>();

        // 解析数据
        Object context;
        try {
            context = processor.deserialize(requestId, task.getTask(), cache);
        } catch (Throwable throwable) {
            monitorService.deserializationError(requestId, task.getTask(), processor, throwable);
            // 这里我们任务反序列化异常是不可重试的，直接将任务结束
            repository.update(taskRequestId, ExecStatus.FINISH, TaskFinishCode.DESERIALIZATION_ERROR, null, null,
                Const.IP);
            return;
        }

        // 调用处理器处理
        ExecResult result;
        Throwable throwable = null;

        String traceContext = Optional.ofNullable(task.getExtMap())
            .map(map -> (String)map.get(AsyncTask.ExtMapKey.TRACE_CONTEXT)).orElse(null);
        Object traceScope = null;
        if (traceService != null && traceContext != null) {
            traceScope = traceService.resume(task.getRetry(), traceContext);
        }

        Long t1 = System.currentTimeMillis();

        try {
            result = processor.process(requestId, context, cache);
            result = result == null ? ExecResult.SUCCESS : result;
        } catch (Throwable e) {
            result = ExecResult.RETRY;
            throwable = e;
        }

        // 是否还需要retry
        boolean retry = false;

        Long t2 = System.currentTimeMillis();

        LOGGER.info(throwable, "任务执行结果：[{}:{}:{}], 总耗时: {}ms, 任务执行耗时: {}ms", requestId, result, context, t2 - t0,
            t2 - t1);
        try {
            switch (result) {
                case SUCCESS:
                    finishTask(repository, processor, requestId, context, TaskFinishCode.SUCCESS, null, cache);
                    break;
                case WAIT:
                    retry = true;
                    repository.update(requestId, ExecStatus.WAIT, null, null, null, Const.IP);
                    break;
                case RETRY:
                    int retryCount = task.getRetry() + 1;
                    int maxRetry = task.getMaxRetry();
                    // 重试次数是否超限
                    boolean retryOverflow = retryCount > maxRetry;
                    if (retryOverflow
                        || (throwable != null && !processor.canRetry(requestId, context, throwable, cache))) {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug(throwable, "任务不可重试, [{}:{}:{}]", requestId, retryOverflow, context);
                        }
                        // 不可重试
                        TaskFinishCode code =
                            retryOverflow ? TaskFinishCode.RETRY_OVERFLOW : TaskFinishCode.CANNOT_RETRY;
                        monitorService.processError(requestId, code, context, processor, throwable);
                        finishTask(repository, processor, requestId, context, code, throwable, cache);
                    } else {
                        // 可以重试
                        retry = true;
                        long interval = processor.nextExecTimeInterval(requestId, retryCount, context, cache);
                        interval = Math.max(interval, 0);
                        LocalDateTime nextExecTime = LocalDateTime.now().plus(interval, ChronoUnit.MILLIS);

                        // 更新重试次数和下次执行时间，注意把状态修改为READY状态
                        task.setStatus(ExecStatus.READY);
                        task.setExecTime(nextExecTime);
                        task.setRetry(retryCount);

                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug(throwable, "任务重试, [{}:{}:{}]", requestId, nextExecTime, context);
                        }
                        monitorService.processRetry(requestId, context, processor, throwable, nextExecTime);
                        // 任务重新加到内存队列中
                        repository.update(taskRequestId, ExecStatus.READY, null, nextExecTime, retryCount, Const.IP);
                    }
                    break;
                case ERROR:
                    finishTask(repository, processor, requestId, context, TaskFinishCode.USER_ERROR, null, cache);
                    break;
                default:
                    throw new IllegalStateException(StringUtils.format("不支持的结果状态： [{}]", result));
            }
        } finally {
            if (traceService != null && traceContext != null) {
                traceService.finish(traceScope, retry, result, throwable);
            }
        }
    }

    /**
     * 执行任务回调
     * 
     * @param processor
     *            任务处理器
     * @param requestId
     *            任务ID
     * @param context
     *            任务上下文
     * @param code
     *            任务结束原因
     * @param cache
     *            cache
     */
    protected void finishTask(AsyncTaskRepository repository, AbstractAsyncTaskProcessor<Object> processor,
        String requestId, Object context, TaskFinishCode code, Throwable processException, Map<String, Object> cache) {
        try {
            processor.afterProcess(requestId, context, code, processException, cache);
        } catch (RuntimeException | Error throwable) {
            LOGGER.warn(throwable, "任务 [{}:{}:{}] 的回调执行异常，该异常将导致异步任务被重新执行", requestId, code, context);
            throw throwable;
        }

        // 更新
        repository.update(requestId, ExecStatus.FINISH, code, null, null, Const.IP);
    }

}
