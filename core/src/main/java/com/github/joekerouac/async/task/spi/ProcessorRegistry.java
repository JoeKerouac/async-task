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

/**
 * @author JoeKerouac
 * @date 2023-11-09 17:04
 * @since 4.0.0
 */
public interface ProcessorRegistry {

    /**
     * 获取processor
     *
     * @param taskType
     *            task type
     * @return 指定task type对应的processor，不存在时返回null
     */
    <T, P extends AbstractAsyncTaskProcessor<T>> P getProcessor(String taskType);

    /**
     * 注册processor
     * 
     * @param taskType
     *            task type
     * @param processor
     *            对应的处理器
     * @return 老的处理器，如果不存在则返回null
     */
    AbstractAsyncTaskProcessor<?> registerProcessor(String taskType, AbstractAsyncTaskProcessor<?> processor);

    /**
     * 移除指定处理器
     * 
     * @param taskType
     *            taskType
     * @param <T>
     *            处理器任务类型
     * @param <P>
     *            处理器类型
     * @return 处理器
     */
    <T, P extends AbstractAsyncTaskProcessor<T>> P removeProcessor(final String taskType);

    /**
     * 获取所有任务类型
     * 
     * @return 所有任务类型
     */
    Set<String> getAllTaskType();

    /**
     * 添加监听
     *
     * @param listener
     *            监听
     */
    void addListener(TaskProcessorListener listener);

    interface TaskProcessorListener {

        /**
         * 任务处理器注册时回调，处理异常时忽略
         *
         * @param taskType
         *            任务类型
         * @param oldProcessor
         *            旧任务处理器
         * @param newProcessor
         *            任务处理器
         */
        default void onRegister(String taskType, AbstractAsyncTaskProcessor<?> oldProcessor,
            AbstractAsyncTaskProcessor<?> newProcessor) {

        }

        /**
         * 移除任务处理器时回调，处理异常时忽略
         *
         * @param taskType
         *            任务类型
         * @param processor
         *            任务处理器
         */
        default void onRemove(String taskType, AbstractAsyncTaskProcessor<?> processor) {

        }

    }

}
