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
package com.github.joekerouac.async.task.starter.flow;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.joekerouac.async.task.AsyncTaskService;
import com.github.joekerouac.async.task.flow.FlowService;
import com.github.joekerouac.async.task.flow.impl.LogFlowMonitorService;
import com.github.joekerouac.async.task.flow.impl.repository.FlowTaskRepositoryImpl;
import com.github.joekerouac.async.task.flow.impl.repository.TaskNodeMapRepositoryImpl;
import com.github.joekerouac.async.task.flow.impl.repository.TaskNodeRepositoryImpl;
import com.github.joekerouac.async.task.flow.model.FlowServiceConfig;
import com.github.joekerouac.async.task.flow.service.FlowServiceImpl;
import com.github.joekerouac.async.task.flow.spi.ExecuteStrategy;
import com.github.joekerouac.async.task.flow.spi.FlowMonitorService;
import com.github.joekerouac.async.task.flow.spi.FlowTaskRepository;
import com.github.joekerouac.async.task.flow.spi.TaskNodeMapRepository;
import com.github.joekerouac.async.task.flow.spi.TaskNodeRepository;
import com.github.joekerouac.async.task.spi.ConnectionSelector;
import com.github.joekerouac.async.task.spi.IDGenerator;
import com.github.joekerouac.async.task.spi.ProcessorSupplier;
import com.github.joekerouac.async.task.spi.TransactionHook;
import com.github.joekerouac.async.task.starter.flow.annotations.Strategy;
import com.github.joekerouac.async.task.starter.flow.config.FlowServiceConfigModel;
import com.github.joekerouac.common.tools.constant.ExceptionProviderConst;
import com.github.joekerouac.common.tools.string.StringUtils;
import com.github.joekerouac.common.tools.util.Assert;

import lombok.CustomLog;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
@CustomLog
@Configuration
@EnableConfigurationProperties({FlowServiceConfigModel.class})
public class FlowServiceAutoConfiguration {

    @Autowired
    private ApplicationContext context;

    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnMissingBean
    public FlowService flowService(@Autowired FlowServiceConfigModel flowServiceConfigModel,
        @Autowired FlowTaskRepository flowTaskRepository, @Autowired TaskNodeRepository taskNodeRepository,
        @Autowired TaskNodeMapRepository taskNodeMapRepository,
        @Autowired(required = false) FlowMonitorService flowMonitorService,
        @Autowired(required = false) ProcessorSupplier processorSupplier) {
        // 下面这几个bean都是async系统提供的，没有用auto wired，不然IDE会报错
        AsyncTaskService asyncTaskService = context.getBean(AsyncTaskService.class);
        ConnectionSelector connectionSelector = context.getBean(ConnectionSelector.class);
        TransactionHook transactionHook = context.getBean(TransactionHook.class);
        IDGenerator idGenerator = context.getBean(IDGenerator.class);

        LOGGER.debug("当前流式任务服务配置详情为： [{}:{}:{}:{}:{}:{}:{}:{}]", flowServiceConfigModel, flowTaskRepository,
            taskNodeRepository, taskNodeMapRepository, idGenerator, transactionHook, connectionSelector,
            flowMonitorService);
        FlowServiceConfig config = new FlowServiceConfig();
        config.setFlowTaskBatchSize(flowServiceConfigModel.getFlowTaskBatchSize());
        config.setStreamNodeMapBatchSize(flowServiceConfigModel.getStreamNodeMapBatchSize());
        config.setIdGenerator(idGenerator);
        config.setTransactionHook(transactionHook);
        config.setAsyncTaskService(asyncTaskService);
        config.setFlowMonitorService(flowMonitorService == null ? new LogFlowMonitorService() : flowMonitorService);
        config.setFlowTaskRepository(flowTaskRepository);
        config.setTaskNodeRepository(taskNodeRepository);
        config.setTaskNodeMapRepository(taskNodeMapRepository);
        config.setConnectionSelector(connectionSelector);
        config.setProcessorSupplier(processorSupplier);

        Map<String, ExecuteStrategy> strategies = context.getBeansOfType(ExecuteStrategy.class);
        for (final ExecuteStrategy strategy : strategies.values()) {
            Strategy annotation = strategy.getClass().getAnnotation(Strategy.class);
            if (annotation == null) {
                LOGGER.warn("策略bean [{}] 上没有使用注解 [{}] 来声明，该策略将被忽略", strategy, Strategy.class.getName());
                continue;
            }

            Assert.notBlank(annotation.name(),
                StringUtils.format("策略bean [{}] 使用了注解 [{}] 来声明，但是指定的名字（name）是空", strategy, Strategy.class.getName()),
                ExceptionProviderConst.CodeErrorExceptionProvider);

            ExecuteStrategy old = config.getExecuteStrategies().putIfAbsent(annotation.name(), strategy);

            Assert.assertTrue(old == null,
                StringUtils.format("策略 [{}] 名对应了两个策略处理bean，请检查代码, [{}:{}]", annotation.name(), old, strategy),
                ExceptionProviderConst.CodeErrorExceptionProvider);
            LOGGER.info("添加策略bean: [{}:{}]", annotation.name(), strategy);
        }

        return new FlowServiceImpl(config);
    }

    @Bean
    @ConditionalOnMissingBean
    public FlowTaskRepository flowTaskRepository() {
        ConnectionSelector connectionSelector = context.getBean(ConnectionSelector.class);
        LOGGER.info("使用默认flow task repository，当前connectionSelector： [{}]", connectionSelector);
        // 注意：如果当前是单数据源场景下可以这么使用，如果是多数据源，则需要自行实现该逻辑
        return new FlowTaskRepositoryImpl(connectionSelector);
    }

    @Bean
    @ConditionalOnMissingBean
    public TaskNodeRepository taskNodeRepository() {
        ConnectionSelector connectionSelector = context.getBean(ConnectionSelector.class);
        LOGGER.info("使用默认task node repository，当前connectionSelector： [{}]", connectionSelector);
        // 注意：如果当前是单数据源场景下可以这么使用，如果是多数据源，则需要自行实现该逻辑
        return new TaskNodeRepositoryImpl(connectionSelector);
    }

    @Bean
    @ConditionalOnMissingBean
    public TaskNodeMapRepository taskNodeMapRepository() {
        ConnectionSelector connectionSelector = context.getBean(ConnectionSelector.class);
        LOGGER.info("使用默认task node map repository，当前connectionSelector： [{}]", connectionSelector);
        // 注意：如果当前是单数据源场景下可以这么使用，如果是多数据源，则需要自行实现该逻辑
        return new TaskNodeMapRepositoryImpl(connectionSelector);
    }

}
