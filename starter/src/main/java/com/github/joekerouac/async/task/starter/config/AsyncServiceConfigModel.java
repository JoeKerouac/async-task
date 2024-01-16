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

import java.util.List;
import java.util.Set;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;

import com.github.joekerouac.async.task.model.AsyncThreadPoolConfig;

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
     * 数据源名称，如果系统没有提供{@link com.github.joekerouac.async.task.spi.AsyncTransactionManager
     * AsyncTransactionManager}这个bean，则需要提供数据源的名称，该数据源中需要包含我们系统所必须的表
     */
    private String dataSource;

    /**
     * 默认配置
     */
    @NotNull
    @NestedConfigurationProperty
    private Config defaultExecutorConfig = new Config();

    /**
     * 特定processor配置
     */
    private List<ExecutorConfig> executorConfigs;

    @Data
    public static class ExecutorConfig {

        /**
         * 配置所属的处理器列表
         */
        @NotEmpty
        private Set<String> executors;

        /**
         * 处理器对应的配置
         */
        @Valid
        @NotNull
        private Config config;
    }

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
        private long loadInterval = 5000;

        /**
         * 触发定时监控的时间间隔，单位毫秒
         */
        @Min(value = 500, message = "监控间隔不能小于500")
        private long monitorInterval = 5000;

        /**
         * 任务执行超时监控时间，单位毫秒，如果任务执行超过该时间将会触发监控
         */
        @Min(value = 100, message = "任务执行超时监控时间不能小于100")
        private long execTimeout = 5000;

        /**
         * 本机启动后添加的任务（不包含本次启动之前添加的任务）执行完毕后，是否从任务仓库中捞取任务，true表示从任务仓库中捞取任务，此时也有可能会执行其他机器添加的任务；
         */
        private boolean loadTaskFromRepository = true;

        /**
         * 实际执行任务的线程池配置
         */
        @NotNull(message = "实际执行任务的线程池配置不能为null")
        @Valid
        @NestedConfigurationProperty
        private AsyncThreadPoolConfig threadPoolConfig = new AsyncThreadPoolConfig();

    }

}
