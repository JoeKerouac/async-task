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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;

import com.github.joekerouac.async.task.AsyncTaskService;
import com.github.joekerouac.async.task.db.AsyncTransactionManagerImpl;
import com.github.joekerouac.async.task.impl.AsyncTaskRepositoryImpl;
import com.github.joekerouac.async.task.impl.DefaultTaskCacheQueueFactory;
import com.github.joekerouac.async.task.model.AsyncServiceConfig;
import com.github.joekerouac.async.task.model.AsyncTaskExecutorConfig;
import com.github.joekerouac.async.task.service.AsyncTaskServiceImpl;
import com.github.joekerouac.async.task.service.DefaultAsyncTaskProcessorEngineFactory;
import com.github.joekerouac.async.task.spi.AsyncTaskProcessorEngineFactory;
import com.github.joekerouac.async.task.spi.AsyncTaskRepository;
import com.github.joekerouac.async.task.spi.AsyncTransactionManager;
import com.github.joekerouac.async.task.spi.ConnectionManager;
import com.github.joekerouac.async.task.spi.IDGenerator;
import com.github.joekerouac.async.task.spi.MonitorService;
import com.github.joekerouac.async.task.spi.ProcessorRegistry;
import com.github.joekerouac.async.task.spi.TaskCacheQueueFactory;
import com.github.joekerouac.async.task.spi.TraceService;
import com.github.joekerouac.async.task.spi.TransactionHook;
import com.github.joekerouac.async.task.starter.config.AsyncServiceConfigModel;
import com.github.joekerouac.async.task.starter.impl.SpringProcessorRegistry;
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
@Order(200)
public class AsyncServiceAutoConfiguration
    implements ApplicationContextAware, BeanFactoryPostProcessor, ApplicationListener<ApplicationStartedEvent> {

    private ApplicationContext context;

    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        // 应用启动起来后再启动异步任务系统
        AsyncTaskService asyncTaskService = context.getBean(AsyncTaskService.class);
        asyncTaskService.start();
        AsyncServiceConfigModel model = context.getBean(AsyncServiceConfigModel.class);
        if (StringUtils.isBlank(model.getDataSource())) {
            return;
        }

        AutowireCapableBeanFactory autowireCapableBeanFactory = context.getAutowireCapableBeanFactory();
        if (autowireCapableBeanFactory instanceof ConfigurableListableBeanFactory) {
            ConfigurableListableBeanFactory beanFactory = (ConfigurableListableBeanFactory)autowireCapableBeanFactory;
            // 理论上这里只会有一个
            for (String beanName : beanFactory.getBeanNamesForType(ConnectionManager.class)) {
                // 手动添加依赖关系，让系统可以正确关闭
                beanFactory.registerDependentBean(beanName, model.getDataSource());
            }
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }

    @Override
    public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }

    @Bean(destroyMethod = "stop")
    @ConditionalOnMissingBean
    public AsyncTaskService asyncTaskService(@Autowired AsyncServiceConfig config) {
        return new AsyncTaskServiceImpl(config);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessorRegistry processorRegistry() {
        return new SpringProcessorRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public AsyncServiceConfig asyncServiceConfig(@Autowired AsyncServiceConfigModel asyncServiceConfigModel,
        @Autowired AsyncTaskProcessorEngineFactory engineFactory, @Autowired AsyncTaskRepository asyncTaskRepository,
        @Autowired IDGenerator asyncIdGenerator, @Autowired AsyncTransactionManager asyncTransactionManager,
        @Autowired TaskCacheQueueFactory taskCacheQueueFactory, @Autowired ProcessorRegistry processorRegistry,
        @Autowired(required = false) MonitorService monitorService,
        @Autowired(required = false) TraceService traceService) {
        LOGGER.debug("当前异步任务服务配置详情为： [{}:{}:{}:{}:{}]", asyncServiceConfigModel, asyncTaskRepository, asyncIdGenerator,
            asyncTransactionManager, monitorService);

        AsyncServiceConfig config = new AsyncServiceConfig();
        config.setTransactionManager(asyncTransactionManager);
        config.setRepository(asyncTaskRepository);
        config.setTaskCacheQueueFactory(taskCacheQueueFactory);
        config.setIdGenerator(asyncIdGenerator);
        config.setEngineFactory(engineFactory);
        config.setProcessorRegistry(processorRegistry);
        config.setMonitorService(monitorService);
        config.setTraceService(traceService);

        config.setDefaultExecutorConfig(convert(asyncServiceConfigModel.getDefaultExecutorConfig()));

        List<AsyncServiceConfigModel.ExecutorConfig> configs = asyncServiceConfigModel.getExecutorConfigs();
        Map<Set<String>, AsyncTaskExecutorConfig> executorConfigs = new HashMap<>();
        if (!CollectionUtil.isEmpty(configs)) {
            configs.forEach(executorConfig -> executorConfigs.put(executorConfig.getExecutors(),
                convert(executorConfig.getConfig())));
        }

        config.setExecutorConfigs(executorConfigs);
        return config;
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
    public ConnectionManager connectionManager(@Autowired AsyncServiceConfigModel model,
        @Autowired(required = false) DataSource dataSource) throws Throwable {
        // 只有用户没有提供 ConnectionSelector 这个bean才会走到这里，现在我们获取用户指定的数据源来构建 ConnectionSelector
        DataSource usedDataSource;
        if (StringUtils.isNotBlank(model.getDataSource())) {
            usedDataSource = context.getBean(model.getDataSource(), DataSource.class);
        } else {
            usedDataSource = dataSource;
        }

        return (ConnectionManager)Class
            .forName("com.github.joekerouac.async.task.starter.impl.SpringJdbcConnectionManager")
            .getConstructor(DataSource.class).newInstance(usedDataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public AsyncTransactionManager asyncTransactionManager(@Autowired ConnectionManager connectionManager,
        @Autowired(required = false) TransactionHook transactionHook) {
        return new AsyncTransactionManagerImpl(connectionManager, transactionHook);
    }

    @Bean
    @ConditionalOnMissingBean
    public TaskCacheQueueFactory taskCacheQueueFactory() {
        return new DefaultTaskCacheQueueFactory();
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
        executorConfig.setExecTimeout(config.getExecTimeout());
        executorConfig.setLoadTaskFromRepository(config.isLoadTaskFromRepository());
        executorConfig.setThreadPoolConfig(config.getThreadPoolConfig());
        return executorConfig;
    }

}
