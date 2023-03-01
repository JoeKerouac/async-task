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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;

import com.github.joekerouac.async.task.AsyncTaskService;
import com.github.joekerouac.async.task.impl.AsyncTaskRepositoryImpl;
import com.github.joekerouac.async.task.model.AsyncServiceConfig;
import com.github.joekerouac.async.task.service.AsyncTaskServiceImpl;
import com.github.joekerouac.async.task.spi.*;
import com.github.joekerouac.async.task.starter.config.AsyncServiceConfigModel;
import com.github.joekerouac.common.tools.string.StringUtils;

import lombok.CustomLog;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
@CustomLog
@EnableConfigurationProperties({AsyncServiceConfigModel.class})
public class AsyncServiceAutoConfiguration implements ApplicationContextAware {

    private ApplicationContext context;

    @Override
    public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnMissingBean
    public AsyncTaskService asyncTaskService(@Autowired AsyncServiceConfigModel asyncServiceConfigModel,
        @Autowired AsyncTaskRepository asyncTaskRepository, @Autowired IDGenerator asyncIdGenerator,
        @Autowired(required = false) TransactionHook transactionHook,
        @Autowired(required = false) MonitorService monitorService,
        @Autowired(required = false) TraceService traceService) {
        LOGGER.debug("当前异步任务服务配置详情为： [{}:{}:{}:{}:{}]", asyncServiceConfigModel, asyncTaskRepository, asyncIdGenerator,
            transactionHook, monitorService);

        // processor懒加载，只有在第一次请求获取processor的时候才加载，尽量避免下面的循环引用场景 >>
        // >> 业务bean -> AsyncTaskService -> Processor -> 业务bean
        // processor懒加载后，Processor -> 业务bean这个依赖就会断开，循环依赖也会解决；PS：注意，如果有业务bean在bean init方法里边调用AsyncTaskService添加
        // 任务的话会导致Processor提前加载，此时仍然有循环引用的风险；
        ProcessorSupplier supplier = new ProcessorSupplier() {

            volatile Map<String, AbstractAsyncTaskProcessor<?>> processors;

            volatile Map<String, AbstractAsyncTaskProcessor<?>> earlyInitProcessors = new ConcurrentHashMap<>();

            @SuppressWarnings({"unchecked"})
            @Override
            public <T, P extends AbstractAsyncTaskProcessor<T>> P get(String processorName) {
                P p = getFromEarly(processorName);
                if (p != null) {
                    return p;
                }

                // 到了这里，我们就只能初始化所有的processor了
                if (processors == null) {
                    init();
                }

                return (P)processors.get(processorName);
            }

            /**
             * 我们尽量只初始化单个processor，一般来说我们的processor都是processor name + 固定的Processor，所以这里都能搜索到，这样其他processor就不用提前初始化了
             * 
             * @param processorName
             *            processor name
             * @param <T>
             *            processor处理的任务真实类型
             * @param <P>
             *            processor真实类型
             * @return processor，可能为null
             */
            @SuppressWarnings({"unchecked", "rawtypes"})
            private <T, P extends AbstractAsyncTaskProcessor<T>> P getFromEarly(String processorName) {
                Map<String, AbstractAsyncTaskProcessor<?>> earlyInitProcessors = this.earlyInitProcessors;
                if (earlyInitProcessors != null) {
                    P p = (P)earlyInitProcessors.compute(processorName, (key, value) -> {
                        if (value != null) {
                            return value;
                        }

                        String[] beanNames = context.getBeanNamesForType(AbstractAsyncTaskProcessor.class);
                        for (String beanName : beanNames) {
                            if (beanName.toLowerCase().startsWith(processorName.toLowerCase())) {
                                AbstractAsyncTaskProcessor processor =
                                    context.getBean(beanName, AbstractAsyncTaskProcessor.class);
                                for (String s : processor.processors()) {
                                    if (Objects.equals(s, processorName)) {
                                        return processor;
                                    }
                                }
                            }
                        }
                        return null;
                    });

                    if (p != null) {
                        return p;
                    }
                }

                return null;
            }

            @SuppressWarnings("rawtypes")
            private synchronized void init() {
                if (processors != null) {
                    return;
                }

                Map<String, AbstractAsyncTaskProcessor<?>> processors = new HashMap<>();
                String[] beanNames = context.getBeanNamesForType(AbstractAsyncTaskProcessor.class);
                for (final String beanName : beanNames) {
                    AbstractAsyncTaskProcessor processor = context.getBean(beanName, AbstractAsyncTaskProcessor.class);
                    for (String name : processor.processors()) {
                        processors.put(name, processor);
                    }
                }

                this.processors = Collections.unmodifiableMap(processors);
                earlyInitProcessors = null;
            }

        };

        AsyncServiceConfig config = new AsyncServiceConfig();
        config.setRepository(asyncTaskRepository);
        config.setCacheQueueSize(asyncServiceConfigModel.getCacheQueueSize());
        config.setLoadThreshold(asyncServiceConfigModel.getLoadThreshold());
        config.setLoadInterval(asyncServiceConfigModel.getLoadInterval());
        config.setMonitorInterval(asyncServiceConfigModel.getMonitorInterval());
        config.setThreadPoolConfig(asyncServiceConfigModel.getThreadPoolConfig());
        config.setIdGenerator(asyncIdGenerator);
        config.setTransactionHook(transactionHook);
        config.setMonitorService(monitorService);
        config.setProcessorSupplier(supplier);
        config.setTraceService(traceService);

        return new AsyncTaskServiceImpl(config);
    }

    @Bean
    @ConditionalOnMissingBean
    public AsyncTaskRepository asyncTaskRepository(@Autowired ConnectionSelector connectionSelector) {
        LOGGER.info("使用默认异步任务仓库，当前connectionSelector： [{}]", connectionSelector);
        // 注意：如果当前是单数据源场景下可以这么使用，如果是多数据源，则需要自行实现该逻辑
        // 注意：该数据源需要是async task表所在的数据源
        return new AsyncTaskRepositoryImpl(connectionSelector);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = {"org.springframework.transaction.support.TransactionSynchronizationManager"})
    public TransactionHook transactionHook()
        throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        LOGGER.info("使用默认TransactionHook");
        return (TransactionHook)Class.forName("com.github.joekerouac.async.task.starter.impl.SpringJdbcTransactionHook")
            .newInstance();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = {"org.springframework.jdbc.datasource.DataSourceUtils"})
    public ConnectionSelector connectionSelector(@Autowired AsyncServiceConfigModel model) throws Throwable {
        // 只有用户没有提供 ConnectionSelector 这个bean才会走到这里，现在我们获取用户指定的数据源来构建 ConnectionSelector
        DataSource dataSource;
        if (StringUtils.isNotBlank(model.getDataSource())) {
            dataSource = context.getBean(model.getDataSource(), DataSource.class);
        } else {
            dataSource = context.getBean(DataSource.class);
        }

        return (ConnectionSelector)Class
            .forName("com.github.joekerouac.async.task.starter.impl.SpringJdbcConnectionSelector")
            .getConstructor(DataSource.class).newInstance(dataSource);
    }

}
