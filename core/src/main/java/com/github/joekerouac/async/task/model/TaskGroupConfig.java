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

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import com.github.joekerouac.async.task.service.InternalTraceService;
import com.github.joekerouac.async.task.spi.AsyncTaskProcessorEngineFactory;
import com.github.joekerouac.async.task.spi.AsyncTaskRepository;
import com.github.joekerouac.async.task.spi.AsyncTransactionManager;
import com.github.joekerouac.async.task.spi.MonitorService;
import com.github.joekerouac.async.task.spi.ProcessorRegistry;
import com.github.joekerouac.async.task.spi.TaskCacheQueueFactory;
import com.github.joekerouac.async.task.spi.TraceService;

import lombok.Data;

/**
 * @author JoeKerouac
 * @date 2023-11-11 14:11
 * @since 4.0.0
 */
@Data
public class TaskGroupConfig {

    /**
     * 任务缓存队列构建工厂
     */
    @NotNull
    private TaskCacheQueueFactory taskCacheQueueFactory;

    /**
     * 异步任务引擎工厂
     */
    @NotNull
    private AsyncTaskProcessorEngineFactory engineFactory;

    /**
     * 任务队列配置
     */
    @NotNull
    @Valid
    private TaskQueueConfig taskQueueConfig;

    /**
     * 实际执行任务的线程池配置
     */
    @NotNull(message = "实际执行任务的线程池配置不能为null")
    @Valid
    private AsyncThreadPoolConfig threadPoolConfig;

    /**
     * 处理器注册表
     */
    @NotNull
    private ProcessorRegistry processorRegistry;

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
     * 任务执行超时监控时间，单位毫秒，如果任务执行超过该时间将会触发监控
     */
    @Min(value = 100, message = "任务执行超时监控时间不能小于100")
    private long execTimeout = 1000 * 5;

    /**
     * 触发任务执行超时监控的时间间隔，单位毫秒
     */
    @Min(value = 500, message = "监控间隔不能小于500")
    private long monitorInterval = 1000 * 5;

    /**
     * 任务仓库
     */
    @NotNull
    private AsyncTaskRepository repository;

    /**
     * 事务管理器
     */
    @NotNull
    private AsyncTransactionManager transactionManager;

    /**
     * 内部trace生成器，只需要保证本实例唯一即可
     */
    @NotNull
    private InternalTraceService internalTraceService;

}
