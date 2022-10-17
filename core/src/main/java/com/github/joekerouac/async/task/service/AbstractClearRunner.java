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

import lombok.CustomLog;

/**
 * 清理线程
 *
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
@CustomLog
public abstract class AbstractClearRunner implements Runnable {

    /**
     * 执行时间间隔，单位毫秒
     */
    private static final long EXEC_INTERVAL = 1000 * 60 * 5;

    /**
     * 启动标识
     */
    private volatile boolean start;

    public AbstractClearRunner() {
        this.start = true;
        // 兜底，本线程应该是daemon线程的，程序退出时会自动退出
        Runtime.getRuntime().addShutdownHook(new Thread(() -> start = false));
    }

    @Override
    public void run() {
        while (start) {
            try {
                clear();
                Thread.sleep(EXEC_INTERVAL);
            } catch (Throwable throwable) {
                LOGGER.warn(throwable, "任务清理线程执行过程中出错，稍后将会重试");
            }
        }
    }

    /**
     * 清理任务
     */
    protected abstract void clear();

}
