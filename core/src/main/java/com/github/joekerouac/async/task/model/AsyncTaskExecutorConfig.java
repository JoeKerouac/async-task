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

import lombok.Data;

/**
 * 异步任务执行配置
 *
 * @author JoeKerouac
 * @date 2023-03-24 14:22
 * @since 3.0.0
 */
@Data
public class AsyncTaskExecutorConfig {

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
     * 当上次任务捞取为空时下次任务捞取的最小时间间隔，当系统从{@link com.github.joekerouac.async.task.spi.AsyncTaskRepository}中没有获取到任务后必
     * 须等待该时间间隔后才能再次捞取，单位毫秒
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
    private AsyncThreadPoolConfig threadPoolConfig = new AsyncThreadPoolConfig();

}
