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
package com.github.joekerouac.async.task.config;

import java.util.UUID;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.joekerouac.async.task.entity.AsyncTask;
import com.github.joekerouac.async.task.flow.impl.repository.FlowTaskRepositoryImpl;
import com.github.joekerouac.async.task.flow.impl.repository.TaskNodeRepositoryImpl;
import com.github.joekerouac.async.task.flow.model.FlowTask;
import com.github.joekerouac.async.task.flow.model.TaskNode;
import com.github.joekerouac.async.task.flow.spi.FlowTaskRepository;
import com.github.joekerouac.async.task.flow.spi.TaskNodeRepository;
import com.github.joekerouac.async.task.impl.AsyncTaskRepositoryImpl;
import com.github.joekerouac.async.task.impl.MonitorServiceAdaptor;
import com.github.joekerouac.async.task.spi.AsyncTaskRepository;
import com.github.joekerouac.async.task.spi.AsyncTransactionManager;
import com.github.joekerouac.async.task.spi.IDGenerator;
import com.github.joekerouac.async.task.spi.MonitorService;

/**
 * spring接入时需要提供的几个bean， MonitorService则是完全可以作为可选项，如果有需求了则可以选择实现，没有需求不提供该bean即可；
 *
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
@Configuration
public class CommonTestConfig {

    @Bean
    public MonitorService monitorService() {
        // 这里做一个空实现，仅仅是为了示例展示，用户可以自行实现
        return new MonitorServiceAdaptor();
    }

    @Bean
    public IDGenerator idGenerator() {
        return () -> UUID.randomUUID().toString();
    }

    @Bean
    public TaskNodeRepository taskNodeRepository(AsyncTransactionManager transactionManager) {
        return new TaskNodeRepositoryImpl(transactionManager) {
            @Override
            public TaskNode selectForUpdate(String nodeRequestId) {
                // 测试用例使用的是sqllite，不支持for update
                return selectByRequestId(nodeRequestId);
            }
        };
    }

    @Bean
    public FlowTaskRepository flowTaskRepository(AsyncTransactionManager transactionManager) {
        return new FlowTaskRepositoryImpl(transactionManager) {
            @Override
            public FlowTask selectForLock(String requestId) {
                // 测试用例使用的是sqllite，不支持for update
                return select(requestId);
            }
        };
    }

    @Bean
    public AsyncTaskRepository asyncTaskRepository(AsyncTransactionManager transactionManager) {
        return new AsyncTaskRepositoryImpl(transactionManager) {
            @Override
            public AsyncTask selectForUpdate(String requestId) {
                return selectByRequestId(requestId);
            }
        };
    }

}
