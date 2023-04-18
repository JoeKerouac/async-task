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

import java.util.Collection;

import com.github.joekerouac.async.task.entity.AsyncTask;

/**
 * 异步任务执行引擎，内部应该能自动从数据库捞取任务
 *
 * @author JoeKerouac
 * @date 2023-04-18 14:55
 * @since 2.0.3
 */
public interface AsyncTaskProcessorEngine {

    /**
     * 启动
     */
    void start();

    /**
     * 关闭
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
     * 将任务批量添加到队列中，添加完毕后会检查队列是否超长，如果超长则按照执行时间排序，将最晚执行的丢弃
     *
     * @param tasks
     *            要添加的任务
     */
    void addTask(Collection<AsyncTask> tasks);
}
