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

import javax.sql.DataSource;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
@Configuration
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
        @Autowired(required = false) MonitorService monitorService) {
        LOGGER.debug("当前异步任务服务配置详情为： [{}:{}:{}:{}:{}]", asyncServiceConfigModel, asyncTaskRepository, asyncIdGenerator,
            transactionHook, monitorService);
        AsyncServiceConfig config = new AsyncServiceConfig();
        config.setRepository(asyncTaskRepository);
        config.setCacheQueueSize(asyncServiceConfigModel.getCacheQueueSize());
        config.setLoadThreshold(asyncServiceConfigModel.getLoadThreshold());
        config.setLoadInterval(asyncServiceConfigModel.getLoadInterval());
        config.setMonitorInterval(asyncServiceConfigModel.getMonitorInterval());
        config.setThreadPoolConfig(asyncServiceConfigModel.getThreadPoolConfig());
        config.setAutoClear(asyncServiceConfigModel.isAutoClear());
        config.setFinishTaskReserve(asyncServiceConfigModel.getFinishTaskReserve());
        config.setIdGenerator(asyncIdGenerator);
        config.setTransactionHook(transactionHook);
        config.setMonitorService(monitorService);
        AsyncTaskService service = new AsyncTaskServiceImpl(config);

        String[] processors = context.getBeanNamesForType(AbstractAsyncTaskProcessor.class);

        for (final String processor : processors) {
            service.addProcessor(context.getBean(processor, AbstractAsyncTaskProcessor.class));
        }

        return service;
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
