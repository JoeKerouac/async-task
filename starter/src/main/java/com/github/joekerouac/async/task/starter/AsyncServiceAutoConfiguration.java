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
import java.util.Set;
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
import com.github.joekerouac.async.task.db.AsyncTransactionManagerImpl;
import com.github.joekerouac.async.task.impl.AsyncTaskRepositoryImpl;
import com.github.joekerouac.async.task.model.AsyncServiceConfig;
import com.github.joekerouac.async.task.model.AsyncTaskExecutorConfig;
import com.github.joekerouac.async.task.service.AsyncTaskServiceImpl;
import com.github.joekerouac.async.task.service.DefaultAsyncTaskProcessorEngineFactory;
import com.github.joekerouac.async.task.spi.AbstractAsyncTaskProcessor;
import com.github.joekerouac.async.task.spi.AsyncTaskProcessorEngineFactory;
import com.github.joekerouac.async.task.spi.AsyncTaskRepository;
import com.github.joekerouac.async.task.spi.AsyncTransactionManager;
import com.github.joekerouac.async.task.spi.ConnectionManager;
import com.github.joekerouac.async.task.spi.IDGenerator;
import com.github.joekerouac.async.task.spi.MonitorService;
import com.github.joekerouac.async.task.spi.ProcessorSupplier;
import com.github.joekerouac.async.task.spi.TraceService;
import com.github.joekerouac.async.task.spi.TransactionHook;
import com.github.joekerouac.async.task.starter.config.AsyncServiceConfigModel;
import com.github.joekerouac.common.tools.collection.CollectionUtil;
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
    public AsyncTaskService asyncTaskService(@Autowired AsyncServiceConfig config) {
        return new AsyncTaskServiceImpl(config);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessorSupplier processorSupplier() {
        return new ProcessorSupplier() {

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
    }

    @Bean
    @ConditionalOnMissingBean
    public AsyncServiceConfig asyncServiceConfig(@Autowired AsyncServiceConfigModel asyncServiceConfigModel,
        @Autowired AsyncTaskProcessorEngineFactory engineFactory, @Autowired AsyncTaskRepository asyncTaskRepository,
        @Autowired IDGenerator asyncIdGenerator, @Autowired AsyncTransactionManager asyncTransactionManager,
        @Autowired(required = false) MonitorService monitorService,
        @Autowired(required = false) TraceService traceService,
        @Autowired(required = false) ProcessorSupplier processorSupplier) {
        LOGGER.debug("当前异步任务服务配置详情为： [{}:{}:{}:{}:{}]", asyncServiceConfigModel, asyncTaskRepository, asyncIdGenerator,
            asyncTransactionManager, monitorService);

        AsyncServiceConfig config = new AsyncServiceConfig();
        config.setRepository(asyncTaskRepository);
        config.setTransactionManager(asyncTransactionManager);
        config.setIdGenerator(asyncIdGenerator);
        config.setMonitorService(monitorService);
        config.setTraceService(traceService);
        config.setProcessorSupplier(processorSupplier);
        config.setDefaultExecutorConfig(convert(asyncServiceConfigModel.getDefaultExecutorConfig()));
        config.setEngineFactory(engineFactory);

        Map<Set<String>, AsyncServiceConfigModel.Config> configs = asyncServiceConfigModel.getExecutorConfigs();
        Map<Set<String>, AsyncTaskExecutorConfig> executorConfigs = new HashMap<>();
        if (!CollectionUtil.isEmpty(configs)) {
            configs.forEach((key, value) -> executorConfigs.put(key, convert(value)));
        }

        config.setExecutorConfigs(executorConfigs);
        return config;
    }

    /**
     * AsyncServiceConfigModel转换为AsyncTaskExecutorConfig
     * 
     * @param config
     *            AsyncServiceConfigModel
     * @return AsyncTaskExecutorConfig
     */
    private AsyncTaskExecutorConfig convert(AsyncServiceConfigModel.Config config) {
        AsyncTaskExecutorConfig executorConfig = new AsyncTaskExecutorConfig();
        executorConfig.setCacheQueueSize(config.getCacheQueueSize());
        executorConfig.setLoadThreshold(config.getLoadThreshold());
        executorConfig.setLoadInterval(config.getLoadInterval());
        executorConfig.setMonitorInterval(config.getMonitorInterval());
        executorConfig.setThreadPoolConfig(config.getThreadPoolConfig());
        return executorConfig;
    }

    @Bean
    @ConditionalOnMissingBean
    private AsyncTaskProcessorEngineFactory engineFactory() {
        return new DefaultAsyncTaskProcessorEngineFactory();
    }

    @Bean
    @ConditionalOnMissingBean
    public AsyncTaskRepository asyncTaskRepository(@Autowired AsyncTransactionManager transactionManager) {
        LOGGER.info("使用默认异步任务仓库，当前connectionSelector： [{}]", transactionManager);
        // 注意：如果当前是单数据源场景下可以这么使用，如果是多数据源，则需要自行实现该逻辑
        // 注意：该数据源需要是async task表所在的数据源
        return new AsyncTaskRepositoryImpl(transactionManager);
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
    public ConnectionManager connectionManager(@Autowired AsyncServiceConfigModel model) throws Throwable {
        // 只有用户没有提供 ConnectionSelector 这个bean才会走到这里，现在我们获取用户指定的数据源来构建 ConnectionSelector
        DataSource dataSource;
        if (StringUtils.isNotBlank(model.getDataSource())) {
            dataSource = context.getBean(model.getDataSource(), DataSource.class);
        } else {
            dataSource = context.getBean(DataSource.class);
        }

        return (ConnectionManager)Class
            .forName("com.github.joekerouac.async.task.starter.impl.SpringJdbcConnectionManager")
            .getConstructor(DataSource.class).newInstance(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public AsyncTransactionManager asyncTransactionManager(@Autowired ConnectionManager connectionManager,
        @Autowired(required = false) TransactionHook transactionHook) {
        return new AsyncTransactionManagerImpl(connectionManager, transactionHook);
    }

}
