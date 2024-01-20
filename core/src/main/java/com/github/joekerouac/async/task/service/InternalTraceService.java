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

import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.github.joekerouac.common.tools.constant.Const;
import com.github.joekerouac.common.tools.date.DateUtil;
import com.github.joekerouac.common.tools.function.InterruptedTaskWithoutResult;
import com.github.joekerouac.common.tools.lock.LockTaskUtil;
import com.github.joekerouac.common.tools.string.StringUtils;

import lombok.CustomLog;

/**
 * 内部使用，外部请勿使用
 *
 * @author JoeKerouac
 * @date 2024-01-20 09:48:18
 * @since 4.0.0
 */
@CustomLog
public class InternalTraceService {

    private static final int PID_MAX = Const.IS_WINDOWS ? 0x100000 : 0x10000;

    private static final ThreadLocal<String> currentTrace = new ThreadLocal<>();

    private final String pid;

    private long baseTime;

    private final int seqLen;

    private final int seqMax;

    private final AtomicLong counter;

    private final ReadWriteLock lock;

    InternalTraceService() {
        String pidMaxProp = System.getProperty("async.task.pid.max");
        int pidMax = PID_MAX;
        if (StringUtils.isNotBlank(pidMaxProp)) {
            pidMax = Integer.parseInt(pidMaxProp);
        }

        int pid = Integer.parseInt(ManagementFactory.getRuntimeMXBean().getName().split("@")[0]);
        if (pid > pidMax) {
            throw new RuntimeException(String.format("当前pid max: %d, 当前pid: %d, 请使用启动参数-Dasync.task.pid.max=xxx"
                + "(将xxx替换为实际的pid最大值)来指定当前操作系统允许的最大pid值；PS: 可以通过cat /prod/sys/kernel/pid_max来查看", pidMax, pid));
        }

        this.baseTime = System.currentTimeMillis();
        this.pid = String.format("%0" + Integer.toString(pidMax).length() + "d", pid);
        this.seqLen = 4;
        this.seqMax = (int)Math.pow(10, seqLen);
        this.counter = new AtomicLong(0);

        this.lock = new ReentrantReadWriteLock();

        WeakReference<InternalTraceService> reference = new WeakReference<>(this);
        // 5秒刷新一次，矫正一次逻辑时钟
        Thread refreshThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000 * 5);
                    InternalTraceService traceService = reference.get();
                    if (traceService != null) {
                        traceService.refresh();
                        // help gc
                        traceService = null;
                    } else {
                        break;
                    }
                } catch (Throwable throwable) {
                    LOGGER.debug("id刷新线程异常，忽略异常", throwable);
                }
            }
            LOGGER.info("id刷新线程停止");
        }, "id刷新线程");
        refreshThread.setDaemon(true);
        refreshThread.start();
    }

    /**
     * 生成一个本机唯一ID作为trace
     *
     * @return trace
     */
    public String generate() {
        long[] args = LockTaskUtil.runWithLock(lock.readLock(), () -> {
            long value = counter.getAndIncrement();
            // 逻辑时间精确到秒，所以这里进位要乘1000换算成秒
            return new long[] {value, baseTime};
        });

        long value = args[0];
        long baseTime = args[1];

        // 逻辑时间精确到秒，所以这里进位要乘1000换算成秒
        long timestamp = baseTime + (value / seqMax) * 1000;
        String time = DateUtil.getFormatDate(new Date(timestamp), "yyyyMMddHHmmss");
        // transId：machineId + seq
        return time + pid + String.format("%0" + seqLen + "d", value % seqMax);
    }

    public static void runWithTrace(String trace, InterruptedTaskWithoutResult task) throws InterruptedException {
        String old = currentTrace.get();
        currentTrace.set(trace);
        try {
            task.run();
        } finally {
            currentTrace.set(old);
        }
    }

    public static String currentTrace() {
        return currentTrace.get();
    }

    /**
     * 如果当前计数器时间偏移过大则矫正计数器
     */
    private void refresh() {
        LockTaskUtil.runWithLock(lock.writeLock(), () -> {
            long value = counter.get();
            // 逻辑时间精确到秒，所以这里进位要乘1000换算成秒
            long timestamp = baseTime + (value / seqMax) * 1000;
            long currentTimeMillis = System.currentTimeMillis();

            // 这里设置1秒的冗余，如果逻辑时间加上1秒还是小于当前时间，那么重置时间
            if (currentTimeMillis > (timestamp + 1000)) {
                baseTime = currentTimeMillis;
                counter.set(0);
            }
        });
    }

}
