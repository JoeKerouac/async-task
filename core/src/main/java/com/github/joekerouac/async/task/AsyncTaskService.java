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
package com.github.joekerouac.async.task;

import java.time.LocalDateTime;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import com.github.joekerouac.async.task.model.ExecStatus;
import com.github.joekerouac.async.task.model.TransStrategy;
import com.github.joekerouac.async.task.spi.AbstractAsyncTaskProcessor;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public interface AsyncTaskService {

    /**
     * 默认最大重试次数
     */
    int MAX_RETRY = 6;

    /**
     * 启动服务
     */
    void start();

    /**
     * 停止服务
     */
    void stop();

    /**
     * 添加处理器
     *
     * @param processor
     *            处理器
     */
    void addProcessor(AbstractAsyncTaskProcessor<?> processor);

    /**
     * 移除指定处理器
     *
     * @param processorName
     *            处理器名
     * @return 如果指定处理器存在，则将其移除，并且返回
     */
    <T, P extends AbstractAsyncTaskProcessor<T>> P removeProcessor(String processorName);

    /**
     * 获取指定处理器
     *
     * @param processorName
     *            处理器名
     * @return 指定的处理器，如果不存在则返回null
     */
    <T, P extends AbstractAsyncTaskProcessor<T>> P getProcessor(String processorName);

    /**
     * 添加任务，如果当前存在事务，应该加入事务，如果当前没有事务，则不使用事务
     *
     * @param requestId
     *            任务幂等ID，不同的任务相同的幂等ID将被认为是同一个任务，会被忽略
     * @param task
     *            任务task
     */
    default void addTask(@NotBlank @Size(max = 200) String requestId, @NotNull Object task) {
        addTask(requestId, task, MAX_RETRY, LocalDateTime.now(), task.getClass().getSimpleName(),
            TransStrategy.SUPPORTS);
    }

    /**
     * 添加任务，如果当前存在事务，应该加入事务，如果当前没有事务，则不使用事务
     *
     * @param requestId
     *            任务幂等ID，不同的任务相同的幂等ID将被认为是同一个任务，会被忽略
     * @param task
     *            任务task
     * @param maxRetry
     *            任务最大重试次数，-1表示无限重试，不包含第一次执行
     */
    default void addTask(@NotBlank @Size(max = 200) String requestId, @NotNull Object task, @Min(-1) int maxRetry) {
        addTask(requestId, task, maxRetry, LocalDateTime.now(), task.getClass().getSimpleName(),
            TransStrategy.SUPPORTS);
    }

    /**
     * 添加任务，如果当前存在事务，应该加入事务，如果当前没有事务，则不使用事务
     *
     * @param requestId
     *            任务幂等ID，不同的任务相同的幂等ID将被认为是同一个任务，会被忽略
     * @param task
     *            任务task
     * @param maxRetry
     *            任务最大重试次数，-1表示无限重试，不包含第一次执行
     * @param execTime
     *            任务执行时间
     */
    default void addTask(@NotBlank @Size(max = 200) String requestId, @NotNull Object task, @Min(-1) int maxRetry,
        @NotNull LocalDateTime execTime) {
        addTask(requestId, task, maxRetry, execTime, task.getClass().getSimpleName(), TransStrategy.SUPPORTS);
    }

    /**
     * 添加任务
     * 
     * @param requestId
     *            任务幂等ID，不同的任务相同的幂等ID将被认为是同一个任务，会被忽略
     * @param task
     *            任务task
     * @param maxRetry
     *            任务最大重试次数，-1表示无限重试，不包含第一次执行
     * @param execTime
     *            任务执行时间
     * @param taskProcessor
     *            任务执行器
     * @param transStrategy
     *            当前执行事务策略
     */
    void addTask(@NotBlank @Size(max = 200) String requestId, @NotNull Object task, @Min(-1) int maxRetry,
        @NotNull LocalDateTime execTime, @NotBlank @Size(max = 100) String taskProcessor, TransStrategy transStrategy);

    /**
     * 添加任务，任务添加成功后直接将状态修改为WAIT，等待唤醒
     *
     * @param requestId
     *            任务幂等ID，不同的任务相同的幂等ID将被认为是同一个任务，会被忽略
     * @param task
     *            任务task
     * @param maxRetry
     *            任务最大重试次数，-1表示无限重试，不包含第一次执行
     * @param execTime
     *            任务执行时间
     * @param taskProcessor
     *            任务执行器
     * @param transStrategy
     *            当前执行事务策略
     */
    void addTaskWithWait(@NotBlank @Size(max = 200) String requestId, @NotNull Object task, @Min(-1) int maxRetry,
        @NotNull LocalDateTime execTime, @NotBlank @Size(max = 100) String taskProcessor, TransStrategy transStrategy);

    /**
     * 唤醒任务，如果任务处于{@link ExecStatus#WAIT}状态，则任务被唤醒，切换到{@link ExecStatus#READY}状态，如果当前存在事务，应该加入事务，如果当前没有事务，则不使用事务
     * 
     * @param requestId
     *            任务requestId
     */
    default void notifyTask(String requestId) {
        notifyTask(requestId, TransStrategy.SUPPORTS);
    }

    /**
     * 唤醒任务，如果任务处于{@link ExecStatus#WAIT}状态，则任务被唤醒，切换到{@link ExecStatus#READY}状态，如果当前存在事务，应该加入事务，如果当前没有事务，则不使用事务
     *
     * @param requestId
     *            任务requestId
     * @param transStrategy
     *            事务策略
     */
    void notifyTask(String requestId, TransStrategy transStrategy);

}
