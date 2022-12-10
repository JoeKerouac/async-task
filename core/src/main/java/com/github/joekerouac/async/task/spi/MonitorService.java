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
package com.github.joekerouac.async.task.spi;

import java.time.LocalDateTime;
import java.util.List;

import com.github.joekerouac.async.task.entity.AsyncTask;
import com.github.joekerouac.async.task.model.TaskFinishCode;

/**
 * 监控服务，外部实现，所有方法都禁止抛出异常
 * 
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public interface MonitorService {

    /**
     * 重复添加任务
     * 
     * @param requestId
     *            任务requestId
     * @param task
     *            用户的任务
     */
    void duplicateTask(String requestId, Object task);

    /**
     * 没有找到指定任务处理器，一般是编码异常
     *
     * @param requestId
     *            任务创建时的幂等请求ID
     * @param task
     *            序列化后的任务详情
     * @param processor
     *            任务处理器名
     */
    void noProcessor(String requestId, String task, String processor);

    /**
     * 任务执行异常或者用户的任务处理器返回了重试的结果，并且任务将被重试
     * 
     * @param requestId
     *            任务创建时的幂等请求ID
     * @param task
     *            反序列化后的任务对象
     * @param processor
     *            任务处理器
     * @param throwable
     *            异常详情
     * @param execTime
     *            下次重试时间
     */
    void processRetry(String requestId, Object task, Object processor, Throwable throwable, LocalDateTime execTime);

    /**
     * 任务执行过程中发生异常或者用户的任务处理器返回了重试的结果，并且无法重试
     *
     * @param requestId
     *            任务创建时的幂等请求ID
     * @param code
     *            任务结束码
     * @param task
     *            反序列化后的任务对象
     * @param processor
     *            任务处理器
     * @param throwable
     *            异常详情
     */
    void processError(String requestId, TaskFinishCode code, Object task, Object processor, Throwable throwable);

    /**
     * 任务反序列化异常
     * 
     * @param requestId
     *            任务创建是的幂等请求ID
     * @param task
     *            序列化后的任务
     * @param processor
     *            任务处理器
     * @param throwable
     *            异常详情
     */
    void deserializationError(String requestId, String task, Object processor, Throwable throwable);

    /**
     * 当前队列中任务数量，每个一段时间该方法都会被调用一次
     * 
     * @param queueSize
     *            当前队列中任务数量
     */
    void monitor(int queueSize);

    /**
     * 异步任务线程未处理异常，通常不应该有
     *
     * @param thread
     *            发生异常的线程
     * @param e
     *            发生的异常
     */
    void uncaughtException(Thread thread, Throwable e);

    /**
     * 任务执行超时告警，通常不应该有，如果有可能是服务正在执行就被kill -9了
     * 
     * @param tasks
     *            执行超时的任务
     * @param timeout
     *            执行超时时间，单位毫秒
     */
    void taskExecTimeout(List<AsyncTask> tasks, long timeout);

}
