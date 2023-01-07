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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScans;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import com.github.joekerouac.async.task.starter.annotations.AsyncTaskProcessor;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public class AsyncTaskProcessorAutoScanner implements ImportBeanDefinitionRegistrar, EnvironmentAware {

    private Environment environment;

    @Override
    public void setEnvironment(final Environment environment) {
        this.environment = environment;
    }

    @Override
    public void registerBeanDefinitions(final AnnotationMetadata importingClassMetadata,
        final BeanDefinitionRegistry registry) {

        Set<String> basePackages = getBasePackages(importingClassMetadata);

        // 从用户设置的扫描目录中扫描我们自定义的注解
        ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(registry, false, environment);
        scanner.addIncludeFilter(new AnnotationTypeFilter(AsyncTaskProcessor.class));
        scanner.scan(basePackages.toArray(new String[0]));
    }

    /**
     * 从importingClass的AnnotationMetadata中获取用户设置的扫描目录
     * 
     * @param importingClassMetadata
     *            importingClassMetadata
     * @return 用户设置的扫描包配置
     */
    private Set<String> getBasePackages(final AnnotationMetadata importingClassMetadata) {
        Set<AnnotationAttributes> componentScans = attributesForRepeatable(importingClassMetadata,
            ComponentScans.class.getName(), ComponentScan.class.getName());

        // 扫描包
        Set<String> basePackages = new LinkedHashSet<>();

        for (final AnnotationAttributes componentScan : componentScans) {
            String[] basePackagesArray = componentScan.getStringArray("basePackages");
            for (String pkg : basePackagesArray) {
                String[] tokenized = StringUtils.tokenizeToStringArray(environment.resolvePlaceholders(pkg),
                    ConfigurableApplicationContext.CONFIG_LOCATION_DELIMITERS);
                Collections.addAll(basePackages, tokenized);
            }
            for (Class<?> clazz : componentScan.getClassArray("basePackageClasses")) {
                basePackages.add(ClassUtils.getPackageName(clazz));
            }
            if (basePackages.isEmpty()) {
                basePackages.add(ClassUtils.getPackageName(importingClassMetadata.getClassName()));
            }
        }

        return basePackages;
    }

    @SuppressWarnings("all")
    private static Set<AnnotationAttributes> attributesForRepeatable(AnnotationMetadata metadata,
        String containerClassName, String annotationClassName) {

        Set<AnnotationAttributes> result = new LinkedHashSet<>();

        // Direct annotation present?
        addAttributesIfNotNull(result, metadata.getAnnotationAttributes(annotationClassName, false));

        // Container annotation present?
        Map<String, Object> container = metadata.getAnnotationAttributes(containerClassName, false);
        if (container != null && container.containsKey("value")) {
            for (Map<String, Object> containedAttributes : (Map<String, Object>[])container.get("value")) {
                addAttributesIfNotNull(result, containedAttributes);
            }
        }

        // Return merged result
        return Collections.unmodifiableSet(result);
    }

    private static void addAttributesIfNotNull(Set<AnnotationAttributes> result,
        @Nullable Map<String, Object> attributes) {

        if (attributes != null) {
            result.add(AnnotationAttributes.fromMap(attributes));
        }
    }

}
