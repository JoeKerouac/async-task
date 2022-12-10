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
import java.util.Map;
import java.util.stream.Collectors;

import com.github.joekerouac.async.task.entity.AsyncTask;
import com.github.joekerouac.async.task.model.TaskFinishCode;
import com.github.joekerouac.async.task.spi.MonitorService;

/**
 * 监控服务适配器
 *
 * @author JoeKerouac
 * @date 2022-12-10 14:14
 * @since 2.0.0
 */
public class MonitorServiceAdaptor implements MonitorService {

    @Override
    public void duplicateTask(String requestId, Object task) {
        DEFAULT_ASYNC_MONITOR_LOGGER.info("重复添加任务，requestId: [{}], task: [{}]", requestId, task);
    }

    @Override
    public void noProcessor(final String requestId, final String task, final String processor) {
        DEFAULT_ASYNC_MONITOR_LOGGER.error("当前task没有找到指定处理器 [{}:{}:{}]", requestId, task, processor);
    }

    @Override
    public void processRetry(final String requestId, final Object task, final Object processor,
        final Throwable throwable, final LocalDateTime execTime) {
        DEFAULT_ASYNC_MONITOR_LOGGER.warn(throwable, "任务执行时发生了异常，任务将在稍后重试, [{}:{}:{}:{}]", requestId, task, processor,
            execTime);
    }

    @Override
    public void processError(final String requestId, final TaskFinishCode code, final Object task,
        final Object processor, final Throwable throwable) {
        DEFAULT_ASYNC_MONITOR_LOGGER.error(throwable, "异步任务执行失败, [{}:{}:{}:{}]", requestId, code, task, processor);
    }

    @Override
    public void deserializationError(final String requestId, final String task, final Object processor,
        final Throwable throwable) {
        DEFAULT_ASYNC_MONITOR_LOGGER.error(throwable, "任务反序列化失败, [{}:{}:{}]", requestId, task, processor);
    }

    @Override
    public void monitor(final int queueSize) {
        DEFAULT_ASYNC_MONITOR_LOGGER.info("当前队列中任务: [{}]", queueSize);
    }

    @Override
    public void uncaughtException(final Thread thread, final Throwable e) {
        DEFAULT_ASYNC_MONITOR_LOGGER.error(e, "异步任务线程未处理异常, [{}]", thread);
    }

    @Override
    public void taskExecTimeout(List<AsyncTask> tasks, long timeout) {
        Map<String, List<AsyncTask>> collect = tasks.stream().collect(Collectors.groupingBy(AsyncTask::getExecIp));
        collect.forEach((ip, taskList) -> DEFAULT_ASYNC_MONITOR_LOGGER
            .warn("任务执行超时，execIp: [{}], timeout: [{}], tasks: [{}]", ip, timeout, taskList));
    }

}
