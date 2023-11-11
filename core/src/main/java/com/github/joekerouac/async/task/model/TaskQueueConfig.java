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

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import lombok.Data;

/**
 * @author JoeKerouac
 * @date 2023-11-09 17:40
 * @since 4.0.0
 */
@Data
public class TaskQueueConfig {

    /**
     * 当上次任务捞取为空时下次任务捞取的最小时间间隔，当系统从repository中没有获取到任务后必须等待该时间间隔后才能再次捞取，单位毫秒
     */
    @Min(value = 0, message = "上次任务捞取为空时下次任务捞取的最小时间间隔不能小于0")
    private long loadInterval = 1000 * 5;

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
     * 本机启动后添加的任务（不包含本次启动之前添加的任务）执行完毕后，是否从任务仓库中捞取任务，true表示从任务仓库中捞取任务，此时也有可能会执行其他机器添加的任务；
     */
    private boolean loadTaskFromRepository = true;

    /**
     * processor name列表
     */
    @NotNull
    private Set<String> taskTypeGroup;

    /**
     * true表示异步任务引擎只处理{@link #taskTypeGroup}中包含的任务，false表示异步任务处理引擎不应该处理{@link #taskTypeGroup}中包含的任务，而应该处理所有其他任务
     */
    private boolean contain;

}
