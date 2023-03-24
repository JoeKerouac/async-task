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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import com.github.joekerouac.async.task.Const;
import com.github.joekerouac.async.task.entity.AsyncTask;
import com.github.joekerouac.async.task.model.*;
import com.github.joekerouac.async.task.spi.*;
import com.github.joekerouac.common.tools.collection.CollectionUtil;
import com.github.joekerouac.common.tools.collection.Pair;
import com.github.joekerouac.common.tools.constant.ExceptionProviderConst;
import com.github.joekerouac.common.tools.lock.LockTaskUtil;
import com.github.joekerouac.common.tools.scheduler.SchedulerTask;
import com.github.joekerouac.common.tools.scheduler.SimpleSchedulerTask;
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
class AsyncTaskProcessorEngine {

    /**
     * 默认工作线程名
     */
    private static final String DEFAULT_THREAD_NAME = "async-worker";

    /**
     * 捞取任务时最多往后捞取多长时间，单位秒；
     */
    private static final int MAX_TIME = 300;

    /**
     * 队列锁，对于队列的写操作需要添加该锁
     */
    private final ReadWriteLock queueLock;

    /**
     * 队列中有任务可以被消费时唤醒
     */
    private final Condition condition;

    /**
     * 内存中的缓存任务队列，key是任务requestId，value是任务预期执行时间
     */
    private final NavigableSet<Pair<String, LocalDateTime>> queue;

    /**
     * 异步任务配置
     */
    private final AsyncTaskExecutorConfig config;

    /**
     * 捞取异步任务的任务
     */
    private SchedulerTask loadTask;

    /**
     * 最后一次捞取为空的时间戳
     */
    private volatile long lastEmptyLoad;

    /**
     * 记录是否启动
     */
    private volatile boolean start = false;

    /**
     * 工作线程
     */
    private Thread[] workerThreads;

    /**
     * 所有任务处理器
     */
    private final Map<String, AbstractAsyncTaskProcessor<?>> processors;

    private final TaskClearRunner taskClearRunner;

    private final ProcessorSupplier processorSupplier;

    private final TraceService traceService;

    private final AsyncTaskRepository repository;

    private final MonitorService monitorService;

    /**
     * 处理的processor
     */
    private final Set<String> processorGroup;

    /**
     * 只处理指定的processor还是不处理指定的processor，true表示只处理指定的processor
     */
    private final boolean contain;

    public AsyncTaskProcessorEngine(AsyncServiceConfig asyncServiceConfig, AsyncTaskExecutorConfig config,
        TaskClearRunner taskClearRunner, Set<String> processorGroup, boolean contain) {
        this.config = config;
        this.taskClearRunner = taskClearRunner;
        this.processorSupplier = asyncServiceConfig.getProcessorSupplier();
        this.traceService = asyncServiceConfig.getTraceService();
        this.repository = asyncServiceConfig.getRepository();
        this.monitorService = asyncServiceConfig.getMonitorService();
        processors = new ConcurrentHashMap<>();
        this.processorGroup = processorGroup;
        this.contain = contain;

        // 队列中按照时间从小到大排序
        queue =
            new TreeSet<>((t0, t1) -> (int)(t0.getValue().atZone(ZoneOffset.systemDefault()).toInstant().toEpochMilli()
                - t1.getValue().atZone(ZoneOffset.systemDefault()).toInstant().toEpochMilli()));

        queueLock = new ReentrantReadWriteLock();
        condition = queueLock.writeLock().newCondition();
    }

    /**
     * 添加处理器
     *
     * @param processor
     *            处理器
     */
    public void addProcessor(AbstractAsyncTaskProcessor<?> processor) {
        Assert.notNull(processor, "待添加的异步任务处理器不能为空", ExceptionProviderConst.IllegalArgumentExceptionProvider);
        Assert.assertTrue(!CollectionUtil.isEmpty(processor.processors()),
            StringUtils.format("处理器可以处理的任务类型不能为空， [{}]", processor),
            ExceptionProviderConst.IllegalArgumentExceptionProvider);
        for (final String name : processor.processors()) {
            if (processor.autoClear()) {
                taskClearRunner.addClearDesc(name, processor.reserve());
            }

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("注册处理器 [{}:{}]", name, processor);
            }

            AbstractAsyncTaskProcessor<?> old = processors.put(name, processor);
            if (old != null) {
                LOGGER.warn("异步任务处理器[{}]发生变更，使用 [{}] 替换 [{}]", name, processor, old);
            }
        }
    }

    /**
     * 移除指定处理器
     *
     * @param processorName
     *            处理器名
     * @return 如果指定处理器存在，则将其移除，并且返回
     */
    @SuppressWarnings("unchecked")
    public <T, P extends AbstractAsyncTaskProcessor<T>> P removeProcessor(String processorName) {
        P processor = (P)processors.remove(processorName);
        if (processor != null && processor.autoClear()) {
            taskClearRunner.removeClearDesc(processorName);
        }

        return processor;
    }

    /**
     * 获取指定处理器
     *
     * @param processorName
     *            处理器名
     * @return 指定的处理器，如果不存在则返回null
     */
    @SuppressWarnings("unchecked")
    public <T, P extends AbstractAsyncTaskProcessor<T>> P getProcessor(String processorName) {
        P processor = (P)processors.get(processorName);
        if (processor == null && processorSupplier != null) {
            synchronized (processorSupplier) {
                processor = (P)processors.get(processorName);
                if (processor == null) {
                    processor = processorSupplier.get(processorName);
                    if (processor != null) {
                        addProcessor(processor);
                    }
                }
            }
        }

        return processor;
    }

    /**
     * 将任务批量添加到队列中，添加完毕后会检查队列是否超长，如果超长则直接丢弃任务
     *
     * @param tasks
     *            要添加的任务
     */
    public void addTask(Collection<AsyncTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }

        LockTaskUtil.runWithLock(queueLock.writeLock(), () -> {
            LocalDateTime currentFirst = queue.isEmpty() ? null : queue.first().getValue();

            for (final AsyncTask task : tasks) {
                // 这里兜底确保任务没有添加过；PS：其实就算任务添加过，后续执行中还会有检查，问题不大
                if (!this.queue.add(new Pair<>(task.getRequestId(), task.getExecTime()))) {
                    LOGGER.info("任务 [{}] 已经在队列中了，忽略该任务", task);
                }
            }

            // 如果队列超长，则将队列最后的任务删除，注意，这里可能多线程都在处理，不过无所谓，最差也就是队列被删除到长度小于cacheQueueSize
            while (queue.size() - config.getCacheQueueSize() > 0) {
                this.queue.pollLast();
            }

            if (currentFirst == null) {
                condition.signalAll();
            } else {
                // 因为是添加任务，所以这里肯定有值了
                LocalDateTime newFirst = queue.first().getValue();

                // 如果新加任务中有任务的就绪时间是早于当前任务的，应该通知所有线程去重新获取最新任务就绪时间，注意，这里是唤醒所有线程，而不是一个线程
                if (newFirst.isBefore(currentFirst)) {
                    condition.signalAll();
                }
            }
        });
    }

    /**
     * 启动
     */
    public synchronized void start() {
        LOGGER.info("异步任务引擎准备启动...");
        start = true;

        // 捞取异步任务的任务，注意：如果具体上次捞取为空时间没有到捞取时间时，不应该触发任务调度
        loadTask = new SimpleSchedulerTask(() -> {
            // 捞取未来指定时间内的任务
            LocalDateTime now = LocalDateTime.now();
            // 距离上次空捞取的时间间隔
            long interval = System.currentTimeMillis() - lastEmptyLoad;

            if (interval < config.getLoadInterval()) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("当前距离上次空捞取的时间间隔为 [{}ms] ，小于系统配置的最小空捞取间隔 [{}ms]，跳过", interval,
                        config.getLoadInterval());
                }
                return;
            }

            // 获取当前队列中的所有任务的ID列表
            List<String> requestIds = LockTaskUtil.runWithLock(queueLock.readLock(),
                () -> queue.stream().map(Pair::getKey).collect(Collectors.toList()));

            // 这里捞取的任务应该不仅能填充队列剩余大小，还应该可以多捞取一些，因为存在这样的情况：本机缓存的任务都是未来将要执行的，而任务仓库中有大量其他
            // 服务示例存储的当前要立即执行的任务，此时如果只捞取队列剩余空间数量的任务，可能会导致其他任务无法被捞取
            int cacheQueueSize = config.getCacheQueueSize();
            int loadSize = (cacheQueueSize - requestIds.size()) * 2 + 5;
            loadSize = Math.min(loadSize, cacheQueueSize);

            // 从任务仓库中捞取任务
            List<AsyncTask> tasks = repository.selectPage(ExecStatus.READY, now.plusSeconds(MAX_TIME), requestIds, 0,
                loadSize, processorGroup, contain);

            if (tasks.isEmpty()) {
                // 没有捞取到任务，记录下本次捞取
                lastEmptyLoad = System.currentTimeMillis();
            } else {
                addTask(tasks);
            }

        }, "task-load", true);
        loadTask.setFixedDelay(config.getLoadInterval());
        loadTask.start();

        // 监控线程设置为daemon线程，系统关闭的时候不用处理
        Thread monitorThread = new Thread(() -> {
            while (start) {
                try {
                    Thread.sleep(config.getMonitorInterval());
                    LockTaskUtil.runWithLock(queueLock.readLock(), () -> monitorService.monitor(queue.size()));

                    // 统计在指定时间之前就开始执行的任务
                    LocalDateTime execTime = LocalDateTime.now().plus(-config.getExecTimeout(), ChronoUnit.MILLIS);
                    List<AsyncTask> tasks = repository.stat(execTime);
                    if (!tasks.isEmpty()) {
                        monitorService.taskExecTimeout(tasks, config.getExecTimeout());
                    }
                } catch (Throwable throwable) {
                    if (!(throwable instanceof InterruptedException)) {
                        LOGGER.info(throwable, "监听线程异常");
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
                // 默认使用加载本类的class loader作为线程的上下文loader
                ClassLoader loader = threadPoolConfig.getDefaultContextClassLoader() == null
                    ? AsyncTaskProcessorEngine.class.getClassLoader() : threadPoolConfig.getDefaultContextClassLoader();

                while (start) {
                    currentThread.setContextClassLoader(loader);

                    try {
                        scheduler();
                    } catch (InterruptedException e) {
                        // 中断异常忽略
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("异步任务工作线程收到中断消息，忽略该消息");
                        }
                    } catch (Throwable throwable) {
                        monitorService.uncaughtException(currentThread, throwable);
                    }
                }
            }, StringUtils.getOrDefault(threadPoolConfig.getThreadName(), DEFAULT_THREAD_NAME) + "-" + i);
            // 强制设置为非daemon线程
            thread.setDaemon(false);
            thread.start();

            workerThreads[i] = thread;
        }
        LOGGER.info("异步任务引擎启动成功...");
    }

    /**
     * 关闭
     */
    public synchronized void stop() {
        LOGGER.info("异步任务引擎准备关闭...");
        start = false;
        loadTask.stop();
        // 主动将线程interrupt掉
        for (final Thread thread : workerThreads) {
            thread.interrupt();
        }
        LockTaskUtil.runWithLock(queueLock.writeLock(), queue::clear);
        LOGGER.info("异步任务引擎关闭成功...");
    }

    /**
     * 任务执行调度方法，每次调用都会从队列中获取一个当前可以执行的任务然后执行
     *
     * @throws InterruptedException
     *             中断异常
     */
    private void scheduler() throws InterruptedException {
        AsyncTask task = take();
        if (task == null) {
            return;
        }

        runTask(task);
    }

    /**
     * 执行任务
     * 
     * @param task
     *            要执行的任务
     */
    private void runTask(AsyncTask task) {
        String taskRequestId = task.getRequestId();

        // 如果此时任务还不能执行，则将任务重新加到队列中
        LocalDateTime now = LocalDateTime.now();

        if (task.getExecTime().isAfter(now)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("任务 [{}] 未到执行时间，不执行，跳过执行, 当前时间：[{}]", task, now);
            }
            // 将任务解锁，重新设置为READY状态
            task.setStatus(ExecStatus.READY);
            repository.update(taskRequestId, ExecStatus.READY, null, null, null, null);
            // 重新添加到队列中
            addTask(Collections.singletonList(task));
            return;
        }

        // 查找任务处理器
        AbstractAsyncTaskProcessor<Object> processor = getProcessor(task.getProcessor());

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
        if (traceService != null && traceContext != null) {
            traceService.resume(task.getRetry(), traceContext);
        }

        try {
            result = processor.process(requestId, context, cache);
            result = result == null ? ExecResult.SUCCESS : result;
        } catch (Throwable e) {
            result = ExecResult.RETRY;
            throwable = e;
        }

        // 是否还需要retry
        boolean retry = false;

        try {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(throwable, "任务执行结果：[{}:{}:{}]", requestId, result, context);
            }

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
                        // 注意，加入队列一定要等到update成功后再加，不然执行时间可能会出错
                        addTask(Collections.singletonList(task));
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
                traceService.finish(retry, result, throwable);
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
    private void finishTask(AsyncTaskRepository repository, AbstractAsyncTaskProcessor<Object> processor,
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

    /**
     * 获取并锁定一个任务
     *
     * @return 任务，可能为空
     */
    private AsyncTask take() {
        String taskRequestId = takeFromMemory();

        if (taskRequestId == null) {
            LOGGER.info("系统关闭，停止调度");
            return null;
        }

        AsyncTask task;
        // 锁定任务，使用CAS更新的形式来完成
        int casUpdateResult = repository.casUpdate(taskRequestId, ExecStatus.READY, ExecStatus.RUNNING, Const.IP);
        while (casUpdateResult <= 0) {
            // 如果CAS更新失败，则从数据库刷新任务，看任务是否已经不一致了
            task = repository.selectByRequestId(taskRequestId);
            ExecStatus status = task.getStatus();

            // 如果任务已经不是READY状态，那么就无需处理了
            if (status != ExecStatus.READY) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("任务 [{}] 已经在其他机器处理了，无需重复处理", task);
                }
                return null;
            }

            // 继续尝试CAS，一般来说走不到这里，因为上边CAS更新失败应该是任务状态已经变更或者有其他并发线程/进程已经将该任务状态更新了
            casUpdateResult = repository.casUpdate(taskRequestId, ExecStatus.READY, ExecStatus.RUNNING, Const.IP);
        }

        // 任务锁定后从数据库刷新任务状态，因为内存中的可能已经不对了
        task = repository.selectByRequestId(taskRequestId);
        return task;
    }

    /**
     * 从队列中获取一个到期任务
     * 
     * @return 队列中的到期任务ID，当系统关闭时会返回null
     */
    private String takeFromMemory() {
        return LockTaskUtil.runWithLock(queueLock.writeLock(), () -> {
            while (start) {
                // 默认等待时间5秒，如果没有任务时会使用该值
                long waitTime = 5000;

                if (!queue.isEmpty()) {
                    Pair<String, LocalDateTime> pair = queue.first();

                    LocalDateTime execTime = pair.getValue();
                    LocalDateTime now = LocalDateTime.now();

                    // 计算第一个任务需要等待的时间，使用 execTime - now
                    waitTime = ChronoUnit.MILLIS.between(now, execTime);

                    // 如果等待时间小于等于0了，表示任务已经就绪了，直接返回即可
                    if (waitTime <= 0) {
                        // 将第一个删除
                        queue.pollFirst();
                        // 判断当前队列大小，如果到达了捞取阈值则触发捞取
                        if (queue.size() < config.getLoadThreshold()) {
                            loadTask.scheduler();
                        }

                        return pair.getKey();
                    }
                }

                // 这里设置最多等待到第一个任务就绪时间，如果有更早的就绪的任务插入，则也可以直接唤醒这个检查
                try {
                    if (!condition.await(waitTime, TimeUnit.MILLISECONDS)) {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("唤醒等待超时，自动唤醒检查");
                        }
                    }
                } catch (InterruptedException e) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("队列取任务线程等待被打断");
                    }
                }
            }

            // 系统关闭时返回null
            return null;
        });
    }

}
