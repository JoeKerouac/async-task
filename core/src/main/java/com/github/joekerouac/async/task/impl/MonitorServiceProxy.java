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

import static com.github.joekerouac.async.task.Const.DEFAULT_ASYNC_MONITOR_LOGGER;

import java.time.LocalDateTime;
import java.util.List;

import com.github.joekerouac.async.task.entity.AsyncTask;
import com.github.joekerouac.async.task.model.TaskFinishCode;
import com.github.joekerouac.async.task.spi.MonitorService;

/**
 * 监控服务包装，内部使用，外部请勿直接使用
 * 
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public final class MonitorServiceProxy implements MonitorService {

    private final MonitorService monitorService;

    public MonitorServiceProxy(final MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @Override
    public void duplicateTask(String requestId, Object task) {
        if (monitorService != null) {
            runWithoutEx(() -> monitorService.duplicateTask(requestId, task));
        }
    }

    @Override
    public void noProcessor(final String requestId, final String task, final String processor) {
        if (monitorService != null) {
            runWithoutEx(() -> monitorService.noProcessor(requestId, task, processor));
        }
    }

    @Override
    public void processRetry(final String requestId, final Object task, final Object processor,
        final Throwable throwable, final LocalDateTime execTime) {
        if (monitorService != null) {
            runWithoutEx(() -> monitorService.processRetry(requestId, task, processor, throwable, execTime));
        }
    }

    @Override
    public void processError(final String requestId, final TaskFinishCode code, final Object task,
        final Object processor, final Throwable throwable) {
        if (monitorService != null) {
            runWithoutEx(() -> monitorService.processError(requestId, code, task, processor, throwable));
        }
    }

    @Override
    public void deserializationError(final String requestId, final String task, final Object processor,
        final Throwable throwable) {
        if (monitorService != null) {
            runWithoutEx(() -> monitorService.deserializationError(requestId, task, processor, throwable));
        }
    }

    @Override
    public void monitor(final int queueSize) {
        if (monitorService != null) {
            runWithoutEx(() -> monitorService.monitor(queueSize));
        }
    }

    @Override
    public void uncaughtException(final Thread thread, final Throwable e) {
        if (monitorService != null) {
            runWithoutEx(() -> monitorService.uncaughtException(thread, e));
        }
    }

    @Override
    public void taskExecTimeout(List<AsyncTask> tasks, long timeout) {
        if (monitorService != null) {
            runWithoutEx(() -> monitorService.taskExecTimeout(tasks, timeout));
        }
    }

    /**
     * 执行指定命令，并且不抛出异常
     *
     * @param runnable
     *            命令
     */
    private void runWithoutEx(Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable throwable) {
            DEFAULT_ASYNC_MONITOR_LOGGER.warn(throwable, "用户监控服务执行异常");
        }
    }

}
