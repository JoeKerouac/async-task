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
import java.util.List;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import com.github.joekerouac.async.task.Const;
import com.github.joekerouac.async.task.entity.AsyncTask;
import com.github.joekerouac.async.task.model.ExecStatus;
import com.github.joekerouac.async.task.model.TaskFinishCode;
import com.github.joekerouac.async.task.model.TaskQueueConfig;
import com.github.joekerouac.async.task.spi.AsyncTaskRepository;
import com.github.joekerouac.async.task.spi.TaskCacheQueue;
import com.github.joekerouac.common.tools.collection.Pair;
import com.github.joekerouac.common.tools.lock.LockTaskUtil;
import com.github.joekerouac.common.tools.scheduler.SimpleSchedulerTask;

import lombok.CustomLog;

/**
 * 任务缓存队列
 * 
 * @author JoeKerouac
 * @date 2023-11-09 17:20
 * @since 4.0.0
 */
@CustomLog
public class DefaultTaskCacheQueue implements TaskCacheQueue {

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
    private final NavigableSet<Pair<String, AsyncTask>> queue;

    /**
     * 缓存长度
     */
    private final int cacheQueueSize;

    /**
     * 触发捞取任务的队列长度阈值，当任务缓存队列的实际长度小于等于该值时会触发任务捞取，应该小于{@link #cacheQueueSize}；
     */
    private final int loadThreshold;

    private final SimpleSchedulerTask loadTask;

    private final AsyncTaskRepository repository;

    /**
     * 最后一次捞取为空的时间戳
     */
    private volatile long lastEmptyLoad;

    private volatile boolean start;

    public DefaultTaskCacheQueue(TaskQueueConfig config, AsyncTaskRepository repository) {
        this.cacheQueueSize = config.getCacheQueueSize();
        this.loadThreshold = config.getLoadThreshold();
        this.repository = repository;
        this.start = false;
        queueLock = new ReentrantReadWriteLock();
        condition = queueLock.writeLock().newCondition();

        // 队列中按照时间从小到大排序，如果指定时间一致，则任务创建IP与当前机器一致的在前
        queue = new TreeSet<>((o1, o2) -> {
            AsyncTask task1 = o1.getValue();
            AsyncTask task2 = o2.getValue();
            int result = task1.getExecTime().compareTo(task2.getExecTime());
            if (result != 0) {
                return result;
            }

            String ip1 = task1.getCreateIp();
            String ip2 = task2.getCreateIp();
            if (ip1.equals(ip2)) {
                return 0;
            } else if (Const.IP.equals(ip1)) {
                return -1;
            } else if (Const.IP.equals(ip2)) {
                return 1;
            } else {
                return 0;
            }
        });

        long loadInterval = config.getLoadInterval();
        boolean loadTaskFromRepository = config.isLoadTaskFromRepository();
        Set<String> taskTypeGroup = config.getTaskTypeGroup();
        boolean contain = config.isContain();

        if (loadTaskFromRepository) {
            LOGGER.info("当前需要从数据库中捞取任务执行, taskTypeGroup: [{}], contain: [{}], loadInterval: [{}], cacheQueueSize: [{}]",
                taskTypeGroup, contain, loadInterval, cacheQueueSize);
            SimpleSchedulerTask schedulerTask = new SimpleSchedulerTask(() -> {
                // 捞取未来指定时间内的任务
                LocalDateTime now = LocalDateTime.now();
                // 距离上次空捞取的时间间隔
                long interval = System.currentTimeMillis() - lastEmptyLoad;

                if (interval < loadInterval) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("当前距离上次空捞取的时间间隔为 [{}ms] ，小于系统配置的最小空捞取间隔 [{}ms]，跳过", interval, loadInterval);
                    }
                    return;
                }

                // 获取当前队列中的所有任务的ID列表
                List<String> requestIds = LockTaskUtil.runWithLock(queueLock.readLock(),
                    () -> queue.stream().map(Pair::getKey).collect(Collectors.toList()));

                // 这里捞取的任务应该不仅能填充队列剩余大小，还应该可以多捞取一些，因为存在这样的情况：本机缓存的任务都是未来将要执行的，而任务仓库中有大量其他
                // 服务示例存储的当前要立即执行的任务，此时如果只捞取队列剩余空间数量的任务，可能会导致其他任务无法被捞取
                int loadSize = (cacheQueueSize - requestIds.size()) * 2 + 5;
                loadSize = Math.min(loadSize, cacheQueueSize);

                // 从任务仓库中捞取任务
                List<AsyncTask> tasks = repository.selectPage(ExecStatus.READY, now.plusSeconds(MAX_TIME), requestIds,
                    0, loadSize, taskTypeGroup, contain);

                if (tasks.isEmpty()) {
                    // 没有捞取到任务，记录下本次捞取
                    lastEmptyLoad = System.currentTimeMillis();
                } else {
                    for (AsyncTask task : tasks) {
                        addTask(task);
                    }
                }
            }, "任务捞取线程", true, Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r);
                thread.setDaemon(false);
                thread.setName("任务捞取线程");
                return thread;
            }));

            schedulerTask.setFixedDelay(loadInterval);
            schedulerTask.setInitialDelay(0);
            loadTask = schedulerTask;
        } else {
            LOGGER.warn("当前需要从数据库中捞取任务执行, taskTypeGroup: [{}], contain: [{}]", taskTypeGroup, contain);
            loadTask = null;
        }
    }

    @Override
    public synchronized void start() {
        if (start) {
            return;
        }
        start = true;
        if (loadTask != null) {
            loadTask.start();
        }
    }

    @Override
    public synchronized void stop() {
        if (!start) {
            return;
        }
        start = false;
        if (loadTask != null) {
            loadTask.stop();
        }
    }

    @Override
    public AsyncTask take() throws InterruptedException {
        String taskRequestId;

        do {
            taskRequestId = takeFromMem();
        } while (!lockTask(taskRequestId));

        // 任务锁定后从数据库刷新任务状态，因为内存中的可能已经不对了
        return repository.selectByRequestId(taskRequestId);
    }

    @Override
    public void addTask(AsyncTask task) {
        LockTaskUtil.runWithLock(queueLock.writeLock(), () -> {
            Pair<String, AsyncTask> oldFirst = queue.isEmpty() ? null : queue.first();

            int addSuccessCount = 0;

            if (task.getStatus() != ExecStatus.READY) {
                LOGGER.debug("当前任务状态不是READY，无需添加到内存队列, task: [{}]", task);
                return;
            }

            // 这里兜底确保任务没有添加过；PS：其实就算任务添加过，后续执行中还会有检查，问题不大
            if (!this.queue.add(new Pair<>(task.getRequestId(), task)) && LOGGER.isDebugEnabled()) {
                LOGGER.debug("任务 [{}] 已经在队列中了，忽略该任务", task);
            } else {
                addSuccessCount += 1;
            }

            if (addSuccessCount <= 0) {
                LOGGER.debug("当前并未实际添加内存队列，不进行队列唤醒");
                return;
            }

            // 如果队列超长，则将队列最后的任务删除
            while (queue.size() - cacheQueueSize > 0) {
                Pair<String, AsyncTask> remove = this.queue.pollLast();
                if (remove != null && LOGGER.isDebugEnabled()) {
                    LOGGER.debug("当前任务队列超长，将最晚执行的任务移除, 移除的任务: [{}]", remove.getKey());
                }
            }

            if (oldFirst == null) {
                LOGGER.debug("任务添加完毕，原队列为空，直接唤醒");
                condition.signalAll();
            } else {
                // 因为是添加任务，所以这里肯定有值了
                Pair<String, AsyncTask> newFirst = queue.first();

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("任务添加完毕，开始唤醒处理, oldFirst: [{}], new: [{}]", oldFirst, newFirst);
                }

                // 如果新加任务中有任务的就绪时间是早于当前任务的，应该通知所有线程去重新获取最新任务就绪时间，注意，这里是唤醒所有线程，而不是一个线程
                if (newFirst.getValue().getExecTime().isBefore(oldFirst.getValue().getExecTime())) {
                    condition.signalAll();
                }
            }
        });
    }

    @Override
    public void removeTask(Set<String> taskRequestIds) {
        if (taskRequestIds == null || taskRequestIds.isEmpty()) {
            return;
        }

        LockTaskUtil.runWithLock(queueLock.writeLock(), () -> {
            // 直接将任务从队列移除，注意，可能会把第一个队列移除，导致调度唤醒时第一个任务变化，时间未到，不过没影响，调度可以继续等待
            queue.removeIf(pair -> taskRequestIds.contains(pair.getKey()));
        });

    }

    /**
     * 数据库锁定任务
     * 
     * @param taskRequestId
     *            任务requestId
     * @return true表示锁定成功
     */
    private boolean lockTask(String taskRequestId) {
        while (repository.casUpdate(taskRequestId, ExecStatus.READY, ExecStatus.RUNNING, Const.IP) <= 0) {
            // 如果CAS更新失败，则从数据库刷新任务，看任务是否已经不一致了
            AsyncTask task = repository.selectByRequestId(taskRequestId);
            ExecStatus status = task.getStatus();

            // 如果任务已经不是READY状态，那么就无需处理了
            if (status != ExecStatus.READY) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("任务 [{}] 已经在其他机器处理了，无需重复处理", task);
                }

                String execIp = task.getExecIp();
                // 理论上不应该出现
                if (Objects.equals(execIp, Const.IP) && task.getTaskFinishCode() != TaskFinishCode.CANCEL) {
                    LOGGER.warn("当前任务的执行IP与本主机一致，但是状态不是ready, status: [{}], task: [{}]", status, task);
                }

                // 结束锁定循环，重新从内存队列中捞取数据
                return false;
            }
        }

        return true;
    }

    private String takeFromMem() throws InterruptedException {
        return LockTaskUtil.runInterruptedTaskWithLock(queueLock.writeLock(), () -> {
            long waitTime = -1;

            while (true) {
                while (queue.isEmpty() || waitTime > 0) {
                    if (waitTime < 0) {
                        waitTime = 1000 * 60;
                    }

                    // 这里设置最多等待到第一个任务就绪时间，如果有更早的就绪的任务插入，则也可以直接唤醒这个检查
                    if (!condition.await(waitTime, TimeUnit.MILLISECONDS)) {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("唤醒等待超时，自动唤醒检查");
                        }
                    }

                    // 重置状态
                    waitTime = -1;
                }

                Pair<String, AsyncTask> pair = queue.first();

                LocalDateTime execTime = pair.getValue().getExecTime();
                LocalDateTime now = LocalDateTime.now();

                // 计算now - execTime，判断第一个任务是否应该执行
                waitTime = ChronoUnit.MILLIS.between(now, execTime);

                // 如果等待时间小于等于0了，表示任务已经就绪了，直接返回即可
                if (waitTime <= 0) {
                    // 将第一个删除
                    queue.pollFirst();

                    // 判断当前队列大小，如果到达了捞取阈值则触发捞取
                    if (queue.size() < loadThreshold && loadTask != null) {
                        loadTask.scheduler();
                    }

                    return pair.getKey();
                } else {
                    LOGGER.debug("当前第一个任务执行时间未到, execTime: [{}], now: [{}]", execTime, now);
                }
            }
        });

    }

}
