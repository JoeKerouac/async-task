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
package com.github.joekerouac.async.task.starter.impl;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.BeansException;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.Order;

import com.github.joekerouac.async.task.spi.AbstractAsyncTaskProcessor;
import com.github.joekerouac.async.task.spi.ProcessorRegistry;

import lombok.CustomLog;

/**
 * @author JoeKerouac
 * @date 2023-11-13 10:36
 * @since 4.0.0
 */
@CustomLog
@Order(100)
public class SpringProcessorRegistry
    implements ProcessorRegistry, ApplicationContextAware, ApplicationListener<ApplicationStartedEvent> {

    private ApplicationContext context;

    private final Map<String, AbstractAsyncTaskProcessor<?>> processors = new ConcurrentHashMap<>();

    private volatile boolean init = false;

    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        init();
    }

    @Override
    public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;

    }

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

    @SuppressWarnings("rawtypes")
    private synchronized void init() {
        if (init) {
            return;
        }

        LOGGER.info("初始化异步任务处理器注册表");

        init = true;

        String[] beanNames = context.getBeanNamesForType(AbstractAsyncTaskProcessor.class);
        for (final String beanName : beanNames) {
            AbstractAsyncTaskProcessor processor = context.getBean(beanName, AbstractAsyncTaskProcessor.class);
            for (String name : processor.processors()) {
                AbstractAsyncTaskProcessor<?> old = processors.putIfAbsent(name, processor);
                if (old != null) {
                    LOGGER.warn("当前已经注册了[{}]的处理器[{}], 处理器[{}]将被忽略", name, old.getClass(), processor.getClass());
                }
            }
        }
    }

}
