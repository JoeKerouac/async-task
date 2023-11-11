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

import java.util.Set;

import com.github.joekerouac.async.task.entity.AsyncTask;

/**
 * 任务缓存队列
 * 
 * @author JoeKerouac
 * @date 2023-11-09 17:11
 * @since 4.0.0
 */
public interface TaskCacheQueue {

    /**
     * 启动cache
     */
    void start();

    /**
     * 停止cache
     */
    void stop();

    /**
     * 将任务尝试加到缓存
     *
     * @param task
     *            要添加的任务
     */
    void addTask(AsyncTask task);

    /**
     * 将任务从缓存移除
     *
     * @param taskRequestIds
     *            任务requestId列表
     */
    void removeTask(Set<String> taskRequestIds);

    /**
     * 从队列中获取一个可执行任务（到达执行时间），并将任务从队列移除，如果当前没有可执行的任务，则阻塞到有可执行任务为止
     * 
     * @return 任务，不能为空
     */
    AsyncTask take() throws InterruptedException;

}
