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
package com.github.joekerouac.async.task.starter;

import java.util.Objects;
import java.util.Set;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.ScannedGenericBeanDefinition;
import org.springframework.core.type.AnnotationMetadata;

import com.github.joekerouac.async.task.starter.annotations.AsyncTaskProcessor;

/**
 * 将异步任务处理器的bean设置为懒加载
 *
 * @author JoeKerouac
 * @date 2023-01-06 16:19
 * @since 2.0.1
 */
public class ProcessorBeanDefinitionPostProcessor implements BeanDefinitionRegistryPostProcessor {

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        for (String beanDefinitionName : registry.getBeanDefinitionNames()) {
            BeanDefinition beanDefinition = registry.getBeanDefinition(beanDefinitionName);
            if (beanDefinition instanceof ScannedGenericBeanDefinition) {
                ScannedGenericBeanDefinition scannedGenericBeanDefinition =
                    (ScannedGenericBeanDefinition)beanDefinition;
                if (!isProcessor(scannedGenericBeanDefinition)) {
                    continue;
                }
                // 强行设置bean是lazy的
                scannedGenericBeanDefinition.setLazyInit(true);
            }
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }

    private boolean isProcessor(ScannedGenericBeanDefinition scannedGenericBeanDefinition) {
        AnnotationMetadata metadata = scannedGenericBeanDefinition.getMetadata();
        Set<String> annotationTypes = metadata.getAnnotationTypes();
        long count = annotationTypes.stream().filter(type -> Objects.equals(type, AsyncTaskProcessor.class.getName()))
            .limit(1).count();
        return count > 0;
    }
}
