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
package com.github.joekerouac.async.task.starter.config;

import java.util.Map;
import java.util.Set;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.github.joekerouac.async.task.model.AsyncThreadPoolConfig;
import com.github.joekerouac.async.task.spi.ConnectionSelector;

import lombok.Data;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "async.service")
public class AsyncServiceConfigModel {

    /**
     * 数据源名称，如果系统没有提供{@link ConnectionSelector ConnectionSelector}这个bean，则需要提供数据源的名称，该数据源中需要包含我们系统所必须的表
     */
    private String dataSource;

    /**
     * 默认配置
     */
    @NotNull
    private Config defaultExecutorConfig = new Config();

    /**
     * 特定processor配置
     */
    private Map<Set<String>, Config> executorConfigs;

    @Data
    public static class Config {

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
         * 当上次任务捞取为空时下次任务捞取的最小时间间隔，当系统从repository中没有获取到任务后必须等待该时间间隔后才能再次捞取，单位毫秒
         */
        @Min(value = 0, message = "上次任务捞取为空时下次任务捞取的最小时间间隔不能小于0")
        private long loadInterval = 1000 * 5;

        /**
         * 触发定时监控的时间间隔，单位毫秒
         */
        @Min(value = 500, message = "监控间隔不能小于500")
        private long monitorInterval = 1000 * 5;

        /**
         * 实际执行任务的线程池配置
         */
        @NotNull(message = "实际执行任务的线程池配置不能为null")
        @Valid
        private AsyncThreadPoolConfig threadPoolConfig = new AsyncThreadPoolConfig();

    }

}
