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
package com.github.joekerouac.async.task.spi;

import java.sql.Connection;
import java.sql.SQLException;

import javax.validation.constraints.NotNull;

/**
 * 连接管理器，{@link #get(String)}和{@link #newConnection(String)}的主要区别在于，如果当前用户已经在当前上下文的连接中开启了事务，而保存异步任务
 * 的时候不希望使用当前事务，那么可以选择使用{@link #newConnection(String)}来创建一个新的连接，而不用使用当前上下文的事务；
 * 
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 3.0.0
 */
public interface ConnectionManager {

    /**
     * 根据async task的requestId选择要使用的Connection，如果当前上下文已经存在连接，则将当前连接返回，否则新建一个返回；注意，在同一个事务上下文中，该选择器应该始终返回同一个连接；
     * 
     * @param requestId
     *            async task的requestId，对于分页查询，这个将为null
     * @return 要使用的DataSource，不能为空
     * @throws SQLException
     *             数据库异常
     */
    default Connection get(String requestId) throws SQLException {
        return newConnection(requestId);
    }

    /**
     * 根据async task的requestId选择要使用的Connection，无论当前上下文是否存在连接，都新建一个连接返回，新建连接应该是没有开启事务的
     *
     * @param requestId
     *            async task的requestId，对于分页查询，这个将为null
     * @return 要使用的DataSource，不能为空
     * @throws SQLException
     *             数据库异常
     */
    Connection newConnection(String requestId) throws SQLException;

    /**
     * 归还connection
     * 
     * @param connection
     *            connection
     * @throws SQLException
     *             数据库异常
     */
    void returnConnection(@NotNull Connection connection) throws SQLException;

}
