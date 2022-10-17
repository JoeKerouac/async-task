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

import com.github.joekerouac.common.tools.constant.ExceptionProviderConst;
import com.github.joekerouac.common.tools.exception.DBException;
import com.github.joekerouac.common.tools.string.StringUtils;
import com.github.joekerouac.common.tools.util.Assert;
import com.github.joekerouac.async.task.function.TransExecutor;
import com.github.joekerouac.async.task.function.TransExecutorWithoutResult;
import com.github.joekerouac.async.task.model.TransStrategy;
import com.github.joekerouac.async.task.service.TransactionSynchronizationManager;

/**
 * 连接选择器，{@link #select(String)}和{@link #newConnection(String)}的主要区别在于，如果当前用户已经在当前上下文的连接中开启了事务，而保存异步任务
 * 的时候不希望使用当前事务，那么可以选择使用{@link #newConnection(String)}来创建一个新的连接，而不用使用当前上下文的事务；
 * 
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public interface ConnectionSelector {

    /**
     * 根据async task的requestId选择要使用的Connection，如果当前上下文已经存在连接，则将当前连接返回，否则新建一个返回；注意，在同一个事务上下文中，该选择器应该始终返回同一个连接；
     * 
     * @param requestId
     *            async task的requestId，对于分页查询，这个将为null
     * @return 要使用的DataSource，不能为空
     * @throws SQLException
     *             数据库异常
     */
    default Connection select(String requestId) throws SQLException {
        return newConnection(requestId);
    }

    /**
     * 根据async task的requestId选择要使用的Connection，无论当前上下文是否存在连接，都新建一个连接返回
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

    /**
     * 根据用户事务策略来获取connection，并将其提供给用户的函数以供用户函数执行；该方法主要是为了让使用者从繁杂的事务管理中解放出来，使用者只需要专注于业务逻辑即可；
     *
     * @param requestId
     *            用来获取链接的requestId，查询时允许为空
     * @param strategy
     *            事务策略；不能为空
     * @param transExecutorWithoutResult
     *            用户函数，函数可以得到一个符合指定事务策略的链接；不能为空
     * @throws SQLException
     *             SQL异常
     */
    default void runWithTrans(String requestId, @NotNull TransStrategy strategy,
        @NotNull TransExecutorWithoutResult transExecutorWithoutResult) throws SQLException {
        runWithTrans(requestId, strategy, connection -> {
            transExecutorWithoutResult.execute(connection);
            return null;
        });
    }

    /**
     * 根据用户事务策略来获取connection，并将其提供给用户的函数以供用户函数执行；该方法主要是为了让使用者从繁杂的事务管理中解放出来，使用者只需要专注于业务逻辑即可；
     * 
     * @param requestId
     *            用来获取链接的requestId，查询时允许为空
     * @param strategy
     *            事务策略，不能为空
     * @param transExecutor
     *            用户函数，函数可以得到一个符合指定事务策略的链接，同时函数可以返回一个结果，结果最终将返回给调用方；不能为空
     * @param <T>
     *            实际结果类型
     * @throws SQLException
     *             SQL异常
     */
    default <T> T runWithTrans(String requestId, @NotNull TransStrategy strategy,
        @NotNull TransExecutor<T> transExecutor) throws SQLException {
        Connection connection = select(requestId);
        Assert.notNull(connection, StringUtils.format("当前Connection获取为空，当前requestId: [{}]", requestId),
            ExceptionProviderConst.IllegalStateExceptionProvider);

        TransStrategy oldStrategy = TransactionSynchronizationManager.getTransStrategy();
        TransactionSynchronizationManager.setTransStrategy(strategy);

        try {
            // 执行完用户函数后是否需要提交事务，如果事务是我们内部开的则需要，如果是加入的外部事务，则不需要提交，等待外部提交即可；
            boolean needCommit = false;
            boolean autoCommit = connection.getAutoCommit();
            switch (strategy) {
                case REQUIRED:
                    // 如果当前没有事务，则开启事务
                    if (autoCommit) {
                        connection.setAutoCommit(false);
                        needCommit = true;
                    }
                    break;
                case SUPPORTS:
                    // 不用做任何处理
                    break;
                case MANDATORY:
                    if (autoCommit) {
                        throw new DBException("当前事务执行策略是MANDATORY，但是当前没有事务");
                    }
                    break;
                case REQUIRES_NEW:
                    // 归还当前链接，重新开一个新链接
                    returnConnection(connection);
                    // 注意，这里先设置为null是防止newConnection发生异常导致老的connection被释放两次（finally块儿触发的释放）；
                    connection = null;
                    connection = newConnection(requestId);
                    autoCommit = connection.getAutoCommit();
                    // 开启事务
                    connection.setAutoCommit(false);
                    // 设置需要提交
                    needCommit = true;
                    break;
                case NOT_SUPPORTED:
                    // 如果当前有事务，则需要归还当前链接，重新获取一个新链接，同时不开启事务；
                    if (!autoCommit) {
                        // 归还当前链接，重新开一个新链接
                        returnConnection(connection);
                        // 注意，这里先设置为null是防止newConnection发生异常导致老的connection被释放两次（finally块儿触发的释放）；
                        connection = null;
                        connection = newConnection(requestId);
                        autoCommit = connection.getAutoCommit();
                        // 关闭事务
                        connection.setAutoCommit(true);
                    }
                    break;
                case NEVER:
                    if (!autoCommit) {
                        throw new DBException("当前事务执行策略是NEVER，但是当前存在事务");
                    }
                    break;
                default:
                    throw new UnsupportedOperationException("不支持的事务策略：" + strategy);
            }

            // 链接选择完毕，开始执行用户函数
            // 执行成功标识，true标识执行成功，需要提交事务，否则表示需要回滚事务
            boolean success = false;
            try {
                // 执行用户函数
                T result = transExecutor.execute(connection);
                success = true;
                return result;
            } finally {
                // 只有需要提交时才去commit或者rollback
                if (needCommit) {
                    if (success) {
                        connection.commit();
                    } else {
                        connection.rollback();
                    }
                }

                // 还原链接的autoCommit状态
                connection.setAutoCommit(autoCommit);
            }
        } finally {
            TransactionSynchronizationManager.setTransStrategy(oldStrategy);
            // 注意，connection在某些场景下可能会是null，我们要处理下；
            if (connection != null) {
                returnConnection(connection);
            }
        }
    }

}
