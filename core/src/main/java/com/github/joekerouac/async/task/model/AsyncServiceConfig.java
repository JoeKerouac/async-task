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

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.validation.constraints.NotNull;

import com.github.joekerouac.async.task.spi.*;

import lombok.Data;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
@Data
public class AsyncServiceConfig {

    /**
     * 任务存储仓库，不限制后端存储位置，但是如果后端不是持久化存储可能会导致服务重启后任务丢失；为空时将使用{@link #connectionSelector}构建默认的仓储服务
     */
    private AsyncTaskRepository repository;

    /**
     * 任务仓储对应的链接选择器，如果{@link #repository}为空时这个不能为空
     */
    private ConnectionSelector connectionSelector;

    /**
     * ID生成器，用于生成async task表的ID，不能为null
     */
    @NotNull(message = "id生成器不能为null")
    private IDGenerator idGenerator;

    /**
     * 任务处理器
     */
    private List<AbstractAsyncTaskProcessor<?>> processors;

    /**
     * 事务拦截器，允许为空，为空时可能小概率出现一些问题，例如任务已经执行了，但是添加数据库失败
     */
    private TransactionHook transactionHook;

    /**
     * 监控服务，允许为null，无论外部是否提供监控服务，系统都会提供一个默认的监控服务
     */
    private MonitorService monitorService;

    /**
     * trace服务，允许为空
     */
    private TraceService traceService;

    /**
     * 任务处理器提供者，优先使用静态任务处理器，静态任务处理器不存在时尝试使用从该处理器提供者获取
     */
    private ProcessorSupplier processorSupplier;

    /**
     * 默认异步任务执行器配置
     */
    @NotNull
    private AsyncTaskExecutorConfig defaultExecutorConfig;

    /**
     * 特定异步任务执行器配置，key是processor name集合，value是配置
     */
    private Map<Set<String>, AsyncTaskExecutorConfig> executorConfigs;

}
