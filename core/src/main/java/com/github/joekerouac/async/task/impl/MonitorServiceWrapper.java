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

import com.github.joekerouac.common.tools.log.LoggerFactory;
import com.github.joekerouac.common.tools.log.Logger;
import com.github.joekerouac.async.task.model.TaskFinishCode;
import com.github.joekerouac.async.task.spi.MonitorService;

/**
 * 监控服务包装
 * 
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public class MonitorServiceWrapper implements MonitorService {

    private static final Logger LOGGER = LoggerFactory.getLogger("DEFAULT_ASYNC_MONITOR");

    private final MonitorService monitorService;

    public MonitorServiceWrapper(final MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @Override
    public void noProcessor(final String requestId, final String task, final String processor) {
        LOGGER.info("当前task没有找到指定处理器 [{}:{}:{}]", requestId, task, processor);
        if (monitorService != null) {
            runWithoutEx(() -> monitorService.noProcessor(requestId, task, processor));
        }
    }

    @Override
    public void processRetry(final String requestId, final Object task, final Object processor,
        final Throwable throwable, final LocalDateTime execTime) {
        LOGGER.info(throwable, "任务执行时发生了异常，任务将在稍后重试, [{}:{}:{}:{}]", requestId, task, processor, execTime);
        if (monitorService != null) {
            runWithoutEx(() -> monitorService.processRetry(requestId, task, processor, throwable, execTime));
        }
    }

    @Override
    public void processError(final String requestId, final TaskFinishCode code, final Object task,
        final Object processor, final Throwable throwable) {
        LOGGER.info(throwable, "异步任务执行失败, [{}:{}:{}:{}]", requestId, code, task, processor);
        if (monitorService != null) {
            runWithoutEx(() -> monitorService.processError(requestId, code, task, processor, throwable));
        }
    }

    @Override
    public void deserializationError(final String requestId, final String task, final Object processor,
        final Throwable throwable) {
        LOGGER.info(throwable, "任务反序列化失败, [{}:{}:{}]", requestId, task, processor);
        if (monitorService != null) {
            runWithoutEx(() -> monitorService.deserializationError(requestId, task, processor, throwable));
        }
    }

    @Override
    public void monitor(final int queueSize) {
        LOGGER.info("当前队列中任务: [{}]", queueSize);
        if (monitorService != null) {
            runWithoutEx(() -> monitorService.monitor(queueSize));
        }
    }

    @Override
    public void uncaughtException(final Thread thread, final Throwable e) {
        LOGGER.info(e, "异步任务线程未处理异常, [{}]", thread);
        if (monitorService != null) {
            runWithoutEx(() -> monitorService.uncaughtException(thread, e));
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
            LOGGER.warn(throwable, "用户监控服务执行异常");
        }
    }

}
