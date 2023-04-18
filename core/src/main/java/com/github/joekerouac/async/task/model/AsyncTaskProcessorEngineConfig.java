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

import java.util.Set;

import javax.validation.constraints.NotNull;

import com.github.joekerouac.async.task.service.TaskClearRunner;
import com.github.joekerouac.async.task.spi.AsyncTaskRepository;
import com.github.joekerouac.async.task.spi.MonitorService;
import com.github.joekerouac.async.task.spi.ProcessorSupplier;
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
     * 引擎执行配置
     */
    @NotNull
    private AsyncTaskExecutorConfig executorConfig;

    /*
     * 全局配置
     */

    /**
     * 任务存储仓库
     */
    @NotNull
    private AsyncTaskRepository repository;

    /**
     * 任务清理器
     */
    @NotNull
    private TaskClearRunner taskClearRunner;

    /**
     * processor name列表
     */
    @NotNull
    private Set<String> processorGroup;

    /**
     * true表示异步任务引擎只处理{@link #processorGroup}中包含的任务，false表示异步任务处理引擎不应该处理{@link #processorGroup}中包含的任务，而应该处理所有其他任务
     */
    private boolean contain;

    /**
     * 任务处理器提供者，优先使用静态任务处理器，静态任务处理器不存在时尝试使用从该处理器提供者获取
     */
    private ProcessorSupplier processorSupplier;

    /**
     * trace服务，允许为空
     */
    private TraceService traceService;

    /**
     * 监控服务，允许为null，无论外部是否提供监控服务，系统都会提供一个默认的监控服务
     */
    private MonitorService monitorService;

}
