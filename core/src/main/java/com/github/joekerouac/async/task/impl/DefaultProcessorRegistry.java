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
package com.github.joekerouac.async.task.impl;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import com.github.joekerouac.async.task.spi.AbstractAsyncTaskProcessor;
import com.github.joekerouac.async.task.spi.ProcessorRegistry;

import lombok.CustomLog;

/**
 * @author JoeKerouac
 * @date 2023-11-11 16:22
 * @since 4.0.0
 */
@CustomLog
public class DefaultProcessorRegistry implements ProcessorRegistry {

    private final Map<String, AbstractAsyncTaskProcessor<?>> processors = new ConcurrentHashMap<>();

    private final Set<TaskProcessorListener> listeners = new CopyOnWriteArraySet<>();

    @Override
    public AbstractAsyncTaskProcessor<?> registerProcessor(String taskType, AbstractAsyncTaskProcessor<?> processor) {
        AbstractAsyncTaskProcessor<?> old = processors.put(taskType, processor);
        if (old != null) {
            LOGGER.warn("当前已经注册了[{}]的处理器[{}], 新处理器[{}]将替代老处理器", taskType, old.getClass(), processor.getClass());
        }

        for (TaskProcessorListener listener : listeners) {
            try {
                listener.onRegister(taskType, old, processor);
            } catch (Throwable throwable) {
                LOGGER.warn(throwable, "processor添加回调处理失败: [{}:{}]", listener.getClass(), listener);
            }
        }
        return old;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, P extends AbstractAsyncTaskProcessor<T>> P removeProcessor(String taskType) {
        P processor = (P)processors.remove(taskType);
        if (processor == null) {
            return null;
        }

        for (TaskProcessorListener listener : listeners) {
            try {
                listener.onRemove(taskType, processor);
            } catch (Throwable throwable) {
                LOGGER.warn(throwable, "processor移除回调处理失败: [{}:{}]", listener.getClass(), listener);
            }
        }
        return processor;
    }

    @Override
    public Set<String> getAllTaskType() {
        return new HashSet<>(processors.keySet());
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, P extends AbstractAsyncTaskProcessor<T>> P getProcessor(String taskType) {
        return (P)processors.get(taskType);
    }

    @Override
    public void addListener(TaskProcessorListener listener) {
        listeners.add(listener);
        processors.forEach((key, value) -> {
            try {
                listener.onRegister(key, null, value);
            } catch (Throwable throwable) {
                LOGGER.warn(throwable, "processor添加回调处理失败: [{}:{}]", listener.getClass(), listener);
            }
        });
    }
}
