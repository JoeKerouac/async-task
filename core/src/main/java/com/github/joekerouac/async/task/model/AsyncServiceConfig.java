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

import javax.validation.Valid;
import javax.validation.constraints.Min;
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
     * 任务缓存队列大小，0表示队列无限长，队列设置太小可能会影响性能；
     */
    @Min(value = 0, message = "缓存队列长度不能小于0")
    private int cacheQueueSize = 100;

    /**
     * 触发捞取任务的队列长度阈值，当任务缓存队列的实际长度小于等于该值时会触发任务捞取，应该小于{@link #cacheQueueSize}；
     */
    @Min(value = 0, message = "触发捞取任务的队列长度阈值不能小于0")
    private int loadThreshold = 30;

    /**
     * 当上次任务捞取为空时下次任务捞取的最小时间间隔，当系统从{@link #repository}中没有获取到任务后必须等待该时间间隔后才能再次捞取，单位毫秒
     */
    @Min(value = 0, message = "上次任务捞取为空时下次任务捞取的最小时间间隔不能小于0")
    private long loadInterval = 1000 * 5;

    /**
     * 触发定时监控的时间间隔，单位毫秒
     */
    @Min(value = 500, message = "监控间隔不能小于500")
    private long monitorInterval = 1000 * 5;

    /**
     * 任务执行超时监控时间，单位毫秒，如果任务执行超过该时间将会触发监控
     */
    @Min(value = 100, message = "任务执行超时监控时间不能小于100")
    private long execTimeout = 1000 * 5;

    /**
     * 实际执行任务的线程池配置
     */
    @NotNull(message = "实际执行任务的线程池配置不能为null")
    @Valid
    private AsyncThreadPoolConfig threadPoolConfig;

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
     * 任务处理器提供者，优先使用静态任务处理器，静态任务处理器不存在时尝试使用从该处理器提供者获取
     */
    private ProcessorSupplier processorSupplier;

}
