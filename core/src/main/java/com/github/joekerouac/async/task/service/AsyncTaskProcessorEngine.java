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
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import com.github.joekerouac.common.tools.collection.Pair;
import com.github.joekerouac.common.tools.collection.CollectionUtil;
import com.github.joekerouac.common.tools.constant.ExceptionProviderConst;
import com.github.joekerouac.common.tools.lock.LockTaskUtil;
import com.github.joekerouac.common.tools.scheduler.SchedulerTask;
import com.github.joekerouac.common.tools.scheduler.SimpleSchedulerTask;
import com.github.joekerouac.common.tools.string.StringUtils;
import com.github.joekerouac.common.tools.util.Assert;
import com.github.joekerouac.async.task.Const;
import com.github.joekerouac.async.task.entity.AsyncTask;
import com.github.joekerouac.async.task.model.AsyncServiceConfig;
import com.github.joekerouac.async.task.model.AsyncThreadPoolConfig;
import com.github.joekerouac.async.task.model.ExecResult;
import com.github.joekerouac.async.task.model.ExecStatus;
import com.github.joekerouac.async.task.model.TaskFinishCode;
import com.github.joekerouac.async.task.spi.AbstractAsyncTaskProcessor;
import com.github.joekerouac.async.task.spi.AsyncTaskRepository;
import com.github.joekerouac.async.task.spi.MonitorService;

import lombok.CustomLog;

/**
 * ????????????????????????
 * 
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
@CustomLog
class AsyncTaskProcessorEngine {

    /**
     * ?????????????????????
     */
    private static final String DEFAULT_THREAD_NAME = "async-worker";

    /**
     * ????????????????????????????????????????????????????????????
     */
    private static final int MAX_TIME = 300;

    /**
     * ??????????????????????????????????????????????????????
     */
    private final ReadWriteLock queueLock;

    /**
     * ??????????????????????????????????????????
     */
    private final Condition condition;

    /**
     * ?????????????????????????????????key?????????requestId???value???????????????????????????
     */
    private final NavigableSet<Pair<String, LocalDateTime>> queue;

    /**
     * ??????????????????
     */
    private final AsyncServiceConfig config;

    /**
     * ???????????????????????????
     */
    private SchedulerTask loadTask;

    /**
     * ????????????????????????????????????
     */
    private volatile long lastEmptyLoad;

    /**
     * ??????????????????
     */
    private volatile boolean start = false;

    /**
     * ????????????
     */
    private Thread[] workerThreads;

    /**
     * ?????????????????????
     */
    private final Map<String, AbstractAsyncTaskProcessor<?>> processors;

    public AsyncTaskProcessorEngine(AsyncServiceConfig config) {
        this.config = config;
        processors = new ConcurrentHashMap<>();

        // ???????????????????????????????????????
        queue =
            new TreeSet<>((t0, t1) -> (int)(t0.getValue().atZone(ZoneOffset.systemDefault()).toInstant().toEpochMilli()
                - t1.getValue().atZone(ZoneOffset.systemDefault()).toInstant().toEpochMilli()));

        queueLock = new ReentrantReadWriteLock();
        condition = queueLock.writeLock().newCondition();

        if (config.getProcessors() != null && !config.getProcessors().isEmpty()) {
            for (final AbstractAsyncTaskProcessor<?> processor : config.getProcessors()) {
                addProcessor(processor);
            }
        }
    }

    /**
     * ???????????????
     *
     * @param processor
     *            ?????????
     */
    public void addProcessor(AbstractAsyncTaskProcessor<?> processor) {
        Assert.notNull(processor, "?????????????????????????????????????????????", ExceptionProviderConst.IllegalArgumentExceptionProvider);
        Assert.assertTrue(!CollectionUtil.isEmpty(processor.processors()),
            StringUtils.format("??????????????????????????????????????????????????? [{}]", processor),
            ExceptionProviderConst.IllegalArgumentExceptionProvider);
        for (final String name : processor.processors()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("??????????????? [{}:{}]", name, processor);
            }

            AbstractAsyncTaskProcessor<?> old = processors.put(name, processor);
            if (old != null) {
                LOGGER.warn("?????????????????????[{}]????????????????????? [{}] ?????? [{}]", name, processor, old);
            }
        }
    }

    /**
     * ?????????????????????
     *
     * @param processorName
     *            ????????????
     * @return ????????????????????????????????????????????????????????????
     */
    @SuppressWarnings("unchecked")
    public <T, P extends AbstractAsyncTaskProcessor<T>> P removeProcessor(String processorName) {
        return (P)processors.remove(processorName);
    }

    /**
     * ?????????????????????
     *
     * @param processorName
     *            ????????????
     * @return ?????????????????????????????????????????????null
     */
    @SuppressWarnings("unchecked")
    public <T, P extends AbstractAsyncTaskProcessor<T>> P getProcessor(String processorName) {
        return (P)processors.get(processorName);
    }

    /**
     * ??????????????????????????????????????????????????????????????????????????????????????????????????????????????????
     *
     * @param tasks
     *            ??????????????????
     */
    public void addTask(Collection<AsyncTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }

        LockTaskUtil.runWithLock(queueLock.writeLock(), () -> {
            LocalDateTime currentFirst = queue.isEmpty() ? null : queue.first().getValue();

            for (final AsyncTask task : tasks) {
                // ??????????????????????????????????????????PS??????????????????????????????????????????????????????????????????????????????
                if (!this.queue.add(new Pair<>(task.getRequestId(), task.getExecTime()))) {
                    LOGGER.info("?????? [{}] ???????????????????????????????????????", task);
                }
            }

            // ?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????cacheQueueSize
            while (queue.size() - config.getCacheQueueSize() > 0) {
                this.queue.pollLast();
            }

            if (currentFirst == null) {
                condition.signalAll();
            } else {
                // ???????????????????????????????????????????????????
                LocalDateTime newFirst = queue.first().getValue();

                // ??????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
                if (newFirst.isBefore(currentFirst)) {
                    condition.signalAll();
                }
            }
        });
    }

    /**
     * ??????
     */
    public synchronized void start() {
        LOGGER.info("??????????????????????????????...");
        start = true;
        AsyncTaskRepository repository = config.getRepository();

        // ?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
        loadTask = new SimpleSchedulerTask(() -> {
            // ????????????????????????????????????
            LocalDateTime now = LocalDateTime.now();
            // ????????????????????????????????????
            long interval = System.currentTimeMillis() - lastEmptyLoad;

            if (interval < config.getLoadInterval()) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("????????????????????????????????????????????? [{}ms] ????????????????????????????????????????????? [{}ms]?????????", interval,
                        config.getLoadInterval());
                }
                return;
            }

            // ???????????????????????????????????????ID??????
            List<String> requestIds = LockTaskUtil.runWithLock(queueLock.readLock(),
                () -> queue.stream().map(Pair::getKey).collect(Collectors.toList()));

            // ??????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
            // ?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
            int cacheQueueSize = config.getCacheQueueSize();
            int loadSize = (cacheQueueSize - requestIds.size()) * 2 + 5;
            loadSize = Math.min(loadSize, cacheQueueSize);

            // ??????????????????????????????
            List<AsyncTask> tasks =
                repository.selectPage(ExecStatus.READY, now.plusSeconds(MAX_TIME), requestIds, 0, loadSize);

            if (tasks.isEmpty()) {
                // ?????????????????????????????????????????????
                lastEmptyLoad = System.currentTimeMillis();
            } else {
                addTask(tasks);
            }

        }, "task-load", true);
        loadTask.setFixedDelay(config.getLoadInterval());
        loadTask.start();

        // ?????????????????????daemon??????????????????????????????????????????
        Thread monitorThread = new Thread(() -> {
            while (start) {
                try {
                    Thread.sleep(config.getMonitorInterval());
                    LockTaskUtil.runWithLock(queueLock.readLock(),
                        () -> config.getMonitorService().monitor(queue.size()));
                } catch (Throwable throwable) {
                    if (!(throwable instanceof InterruptedException)) {
                        LOGGER.info(throwable, "??????????????????");
                    }
                }
            }
        }, "monitor");
        monitorThread.setDaemon(true);
        monitorThread.start();

        AsyncThreadPoolConfig threadPoolConfig = config.getThreadPoolConfig();
        workerThreads = new Thread[threadPoolConfig.getCorePoolSize()];
        for (int i = 0; i < workerThreads.length; i++) {
            Thread thread = new Thread(() -> {
                Thread currentThread = Thread.currentThread();
                // ???????????????????????????class loader????????????????????????loader
                ClassLoader loader = threadPoolConfig.getDefaultContextClassLoader() == null
                    ? AsyncTaskProcessorEngine.class.getClassLoader() : threadPoolConfig.getDefaultContextClassLoader();

                while (start) {
                    currentThread.setContextClassLoader(loader);

                    try {
                        scheduler();
                    } catch (InterruptedException e) {
                        // ??????????????????
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("????????????????????????????????????????????????????????????");
                        }
                    } catch (Throwable throwable) {
                        MonitorService monitorService = config.getMonitorService();
                        monitorService.uncaughtException(currentThread, throwable);
                    }
                }
            }, StringUtils.getOrDefault(threadPoolConfig.getThreadName(), DEFAULT_THREAD_NAME) + "-" + i);
            // ??????????????????daemon??????
            thread.setDaemon(false);
            thread.start();

            workerThreads[i] = thread;
        }
        LOGGER.info("??????????????????????????????...");
    }

    /**
     * ??????
     */
    public synchronized void stop() {
        LOGGER.info("??????????????????????????????...");
        start = false;
        loadTask.stop();
        // ???????????????interrupt???
        for (final Thread thread : workerThreads) {
            thread.interrupt();
        }
        LockTaskUtil.runWithLock(queueLock.writeLock(), queue::clear);
        LOGGER.info("??????????????????????????????...");
    }

    /**
     * ????????????????????????????????????????????????????????????????????????????????????????????????????????????
     * 
     * @throws InterruptedException
     *             ????????????
     */
    private void scheduler() throws InterruptedException {
        AsyncTaskRepository repository = config.getRepository();
        MonitorService monitorService = config.getMonitorService();
        String taskRequestId = take();

        if (taskRequestId == null) {
            LOGGER.info("???????????????????????????");
            return;
        }

        AsyncTask task;
        // ?????????????????????CAS????????????????????????
        int casUpdateResult = repository.casUpdate(taskRequestId, ExecStatus.READY, ExecStatus.RUNNING, Const.IP);
        while (casUpdateResult <= 0) {
            // ??????CAS??????????????????????????????????????????????????????????????????????????????
            task = repository.selectByRequestId(taskRequestId);
            ExecStatus status = task.getStatus();

            // ????????????????????????READY?????????????????????????????????
            if (status != ExecStatus.READY) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("?????? [{}] ???????????????????????????????????????????????????", task);
                }
                return;
            }

            // ????????????CAS?????????????????????????????????????????????CAS????????????????????????????????????????????????????????????????????????/???????????????????????????????????????
            casUpdateResult = repository.casUpdate(taskRequestId, ExecStatus.READY, ExecStatus.RUNNING, Const.IP);
        }

        // ???????????????????????????????????????????????????????????????????????????????????????
        task = repository.selectByRequestId(taskRequestId);

        // ?????????????????????????????????????????????????????????????????????
        LocalDateTime now = LocalDateTime.now();

        if (task.getExecTime().isAfter(now)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("?????? [{}] ?????????????????????????????????????????????, ???????????????[{}]", task, now);
            }
            // ?????????????????????????????????READY??????
            task.setStatus(ExecStatus.READY);
            repository.update(taskRequestId, ExecStatus.READY, null, null, null, null);
            // ????????????????????????
            addTask(Collections.singletonList(task));
            return;
        }

        // ?????????????????????
        @SuppressWarnings("unchecked")
        AbstractAsyncTaskProcessor<Object> processor =
            (AbstractAsyncTaskProcessor<Object>)processors.get(task.getProcessor());

        String requestId = task.getRequestId();
        if (processor == null) {
            monitorService.noProcessor(requestId, task.getTask(), task.getProcessor());
            // ?????????????????????????????????????????????
            repository.update(taskRequestId, ExecStatus.FINISH, TaskFinishCode.NO_PROCESSOR, null, null, Const.IP);
            return;
        }

        Map<String, Object> cache = new HashMap<>();

        // ????????????
        Object context;
        try {
            context = processor.deserialize(requestId, task.getTask(), cache);
        } catch (Throwable throwable) {
            monitorService.deserializationError(requestId, task.getTask(), processor, throwable);
            // ??????????????????????????????????????????????????????????????????????????????
            repository.update(taskRequestId, ExecStatus.FINISH, TaskFinishCode.DESERIALIZATION_ERROR, null, null,
                Const.IP);
            return;
        }

        // ?????????????????????
        ExecResult result;
        Throwable throwable = null;
        try {
            result = processor.process(requestId, context, cache);
            result = result == null ? ExecResult.SUCCESS : result;
        } catch (Throwable e) {
            result = ExecResult.RETRY;
            throwable = e;
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(throwable, "?????????????????????[{}:{}:{}]", requestId, result, context);
        }

        switch (result) {
            case SUCCESS:
                finishTask(repository, processor, requestId, context, TaskFinishCode.SUCCESS, null, cache);
                break;
            case WAIT:
                repository.update(requestId, ExecStatus.WAIT, null, null, null, Const.IP);
                break;
            case RETRY:
                int retry = task.getRetry() + 1;
                int maxRetry = task.getMaxRetry();
                // ????????????????????????
                boolean retryOverflow = retry > maxRetry;
                if (retryOverflow || (throwable != null && !processor.canRetry(requestId, context, throwable, cache))) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug(throwable, "??????????????????, [{}:{}:{}]", requestId, retryOverflow, context);
                    }
                    // ????????????
                    TaskFinishCode code = retryOverflow ? TaskFinishCode.RETRY_OVERFLOW : TaskFinishCode.CANNOT_RETRY;
                    monitorService.processError(requestId, code, context, processor, throwable);
                    finishTask(repository, processor, requestId, context, code, throwable, cache);
                } else {
                    // ????????????
                    long interval = processor.nextExecTimeInterval(requestId, retry, context, cache);
                    interval = Math.max(interval, 0);
                    LocalDateTime nextExecTime = LocalDateTime.now().plus(interval, ChronoUnit.MILLIS);

                    // ??????????????????????????????????????????????????????????????????READY??????
                    task.setStatus(ExecStatus.READY);
                    task.setExecTime(nextExecTime);
                    task.setRetry(retry);

                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug(throwable, "????????????, [{}:{}:{}]", requestId, nextExecTime, context);
                    }
                    monitorService.processRetry(requestId, context, processor, throwable, nextExecTime);
                    // ?????????????????????????????????
                    repository.update(taskRequestId, ExecStatus.READY, null, nextExecTime, retry, Const.IP);
                    // ????????????????????????????????????update???????????????????????????????????????????????????
                    addTask(Collections.singletonList(task));
                }
                break;
            case ERROR:
                finishTask(repository, processor, requestId, context, TaskFinishCode.USER_ERROR, null, cache);
                break;
            default:
                throw new IllegalStateException(StringUtils.format("??????????????????????????? [{}]", result));
        }
    }

    /**
     * ??????????????????
     * 
     * @param processor
     *            ???????????????
     * @param requestId
     *            ??????ID
     * @param context
     *            ???????????????
     * @param code
     *            ??????????????????
     * @param cache
     *            cache
     */
    private void finishTask(AsyncTaskRepository repository, AbstractAsyncTaskProcessor<Object> processor,
        String requestId, Object context, TaskFinishCode code, Throwable processException, Map<String, Object> cache) {
        try {
            processor.afterProcess(requestId, context, code, processException, cache);
        } catch (RuntimeException | Error throwable) {
            LOGGER.warn(throwable, "?????? [{}:{}:{}] ?????????????????????????????????????????????????????????????????????", requestId, code, context);
            throw throwable;
        }

        // ??????
        repository.update(requestId, ExecStatus.FINISH, code, null, null, Const.IP);
    }

    /**
     * ????????????????????????????????????
     * 
     * @return ????????????????????????ID??????????????????????????????null
     */
    private String take() {
        return LockTaskUtil.runWithLock(queueLock.writeLock(), () -> {
            while (start) {
                // ??????????????????5??????????????????????????????????????????
                long waitTime = 5000;

                if (!queue.isEmpty()) {
                    Pair<String, LocalDateTime> pair = queue.first();

                    LocalDateTime execTime = pair.getValue();
                    LocalDateTime now = LocalDateTime.now();

                    // ??????????????????????????????????????????????????? execTime - now
                    waitTime = ChronoUnit.MILLIS.between(now, execTime);

                    // ??????????????????????????????0??????????????????????????????????????????????????????
                    if (waitTime <= 0) {
                        // ??????????????????
                        queue.pollFirst();
                        // ?????????????????????????????????????????????????????????????????????
                        if (queue.size() < config.getLoadThreshold()) {
                            loadTask.scheduler();
                        }

                        return pair.getKey();
                    }
                }

                // ???????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
                try {
                    if (!condition.await(waitTime, TimeUnit.MILLISECONDS)) {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("???????????????????????????????????????");
                        }
                    }
                } catch (InterruptedException e) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("????????????????????????????????????");
                    }
                }
            }

            // ?????????????????????null
            return null;
        });
    }

}
