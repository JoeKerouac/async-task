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

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DataSourceUtils;

import com.github.joekerouac.async.task.spi.ConnectionSelector;

/**
 * 依赖于spring jdbc实现的链接选择器
 *
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public class SpringJdbcConnectionSelector implements ConnectionSelector {

    private final DataSource dataSource;

    public SpringJdbcConnectionSelector(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Connection select(final String requestId) throws SQLException {
        return DataSourceUtils.doGetConnection(dataSource);
    }

    @Override
    public Connection newConnection(final String requestId) throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void returnConnection(final Connection connection) throws SQLException {
        DataSourceUtils.doReleaseConnection(connection, dataSource);
    }

    @Override
    public String toString() {
        return dataSource.toString();
    }

}
