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

import java.util.Map;
import java.util.Set;

import javax.validation.constraints.NotNull;

import com.github.joekerouac.async.task.impl.DefaultProcessorRegistry;
import com.github.joekerouac.async.task.impl.DefaultTaskCacheQueueFactory;
import com.github.joekerouac.async.task.service.DefaultAsyncTaskProcessorEngineFactory;
import com.github.joekerouac.async.task.spi.AsyncTaskProcessorEngineFactory;
import com.github.joekerouac.async.task.spi.AsyncTaskRepository;
import com.github.joekerouac.async.task.spi.AsyncTransactionManager;
import com.github.joekerouac.async.task.spi.IDGenerator;
import com.github.joekerouac.async.task.spi.MonitorService;
import com.github.joekerouac.async.task.spi.ProcessorRegistry;
import com.github.joekerouac.async.task.spi.TaskCacheQueueFactory;
import com.github.joekerouac.async.task.spi.TraceService;

import lombok.Data;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
@Data
public class AsyncServiceConfig {

    /**
     * 默认异步任务执行器配置
     */
    @NotNull
    private AsyncTaskExecutorConfig defaultExecutorConfig;

    /**
     * 特定异步任务执行器配置，key是processor name集合，value是配置
     */
    private Map<Set<String>, AsyncTaskExecutorConfig> executorConfigs;

    /**
     * 事务管理器
     */
    @NotNull(message = "事务管理器不能为空")
    private AsyncTransactionManager transactionManager;

    /**
     * 任务仓库，注意，任务仓库应该能正确处理事务管理器的事务
     */
    @NotNull
    private AsyncTaskRepository repository;

    /**
     * 任务缓存队列构建工厂
     */
    @NotNull
    private TaskCacheQueueFactory taskCacheQueueFactory = new DefaultTaskCacheQueueFactory();

    /**
     * ID生成器，用于生成async task表的ID，不能为null
     */
    @NotNull(message = "id生成器不能为null")
    private IDGenerator idGenerator;

    /**
     * 异步任务引擎工厂
     */
    @NotNull
    private AsyncTaskProcessorEngineFactory engineFactory = new DefaultAsyncTaskProcessorEngineFactory();

    /**
     * 任务处理器注册表
     */
    @NotNull
    private ProcessorRegistry processorRegistry = new DefaultProcessorRegistry();

    /**
     * 监控服务，允许为null，无论外部是否提供监控服务，系统都会提供一个默认的监控服务
     */
    private MonitorService monitorService;

    /**
     * trace服务，允许为空
     */
    private TraceService traceService;

}
