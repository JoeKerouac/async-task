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
package com.github.joekerouac.async.task.test;

import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.joekerouac.async.task.impl.MonitorServiceAdaptor;
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
public class TestConfig {

    @Bean
    public MonitorService monitorService() {
        // 这里做一个空实现，仅仅是为了示例展示，用户可以自行实现
        return new MonitorServiceAdaptor();
    }

    @Bean
    public DataSource asyncDataSource() {
        return TestEngine.initDataSource("spring.async.test.db");
    }

    @Bean
    public IDGenerator idGenerator() {
        return () -> UUID.randomUUID().toString();
    }

}
