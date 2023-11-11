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

import com.github.joekerouac.async.task.spi.AbstractAsyncTaskProcessor;
import com.github.joekerouac.async.task.spi.ProcessorRegistry;

/**
 * @author JoeKerouac
 * @date 2023-11-11 16:22
 * @since 4.0.0
 */
public class DefaultProcessorRegistry implements ProcessorRegistry {

    private Map<String, AbstractAsyncTaskProcessor<?>> processors = new ConcurrentHashMap<>();

    @Override
    public AbstractAsyncTaskProcessor<?> registerProcessor(String taskType, AbstractAsyncTaskProcessor<?> processor) {
        return processors.put(taskType, processor);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, P extends AbstractAsyncTaskProcessor<T>> P removeProcessor(String taskType) {
        return (P)processors.remove(taskType);
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
}
