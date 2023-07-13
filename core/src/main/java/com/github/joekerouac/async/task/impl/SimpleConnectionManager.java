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
package com.github.joekerouac.async.task.impl;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import com.github.joekerouac.common.tools.constant.ExceptionProviderConst;
import com.github.joekerouac.common.tools.util.Assert;
import com.github.joekerouac.async.task.spi.ConnectionManager;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public class SimpleConnectionManager implements ConnectionManager {

    private final DataSource dataSource;

    private static final ThreadLocal<Connection> CONNECTION_THREAD_LOCAL = new ThreadLocal<>();

    private static final ThreadLocal<Integer> COUNTER_THREAD_LOCAL = new ThreadLocal<>();

    public SimpleConnectionManager(DataSource dataSource) {
        Assert.notNull(dataSource, "数据源不能为null", ExceptionProviderConst.IllegalArgumentExceptionProvider);
        this.dataSource = dataSource;
    }

    @Override
    public Connection get(final String requestId) throws SQLException {
        Connection connection = CONNECTION_THREAD_LOCAL.get();
        if (connection == null) {
            connection = dataSource.getConnection();
            COUNTER_THREAD_LOCAL.set(1);
            CONNECTION_THREAD_LOCAL.set(connection);
        } else {
            COUNTER_THREAD_LOCAL.set(COUNTER_THREAD_LOCAL.get() + 1);
        }

        return connection;
    }

    @Override
    public Connection newConnection(final String requestId) throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void returnConnection(final Connection connection) throws SQLException {
        if (CONNECTION_THREAD_LOCAL.get() != connection) {
            connection.close();
            return;
        }
        int counter = COUNTER_THREAD_LOCAL.get() - 1;
        // 计数减一
        COUNTER_THREAD_LOCAL.set(counter);

        // 如果计数已经清零，那么直接移除
        if (counter <= 0) {
            COUNTER_THREAD_LOCAL.remove();
            CONNECTION_THREAD_LOCAL.remove();
            connection.close();
        }
    }
}
