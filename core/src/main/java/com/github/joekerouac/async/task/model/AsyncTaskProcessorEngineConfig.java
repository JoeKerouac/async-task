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
package com.github.joekerouac.async.task.model;

import javax.validation.constraints.NotNull;

import com.github.joekerouac.async.task.service.InternalTraceService;
import com.github.joekerouac.async.task.spi.AsyncTaskRepository;
import com.github.joekerouac.async.task.spi.MonitorService;
import com.github.joekerouac.async.task.spi.ProcessorRegistry;
import com.github.joekerouac.async.task.spi.TaskCacheQueue;
import com.github.joekerouac.async.task.spi.TraceService;

import lombok.Data;

/**
 * @author JoeKerouac
 * @date 2023-04-18 15:24
 * @since 2.0.3
 */
@Data
public class AsyncTaskProcessorEngineConfig {

    /**
     * 引擎线程配置
     */
    @NotNull
    private AsyncThreadPoolConfig asyncThreadPoolConfig;

    /**
     * 处理器注册表
     */
    @NotNull
    private ProcessorRegistry processorRegistry;

    /**
     * 任务队列
     */
    @NotNull
    private TaskCacheQueue taskCacheQueue;

    /**
     * trace服务，允许为空
     */
    private TraceService traceService;

    /**
     * 监控服务
     */
    @NotNull
    private MonitorService monitorService;

    /**
     * 任务仓库
     */
    @NotNull
    private AsyncTaskRepository repository;

    /**
     * 内部trace
     */
    @NotNull
    private InternalTraceService internalTraceService;
}
