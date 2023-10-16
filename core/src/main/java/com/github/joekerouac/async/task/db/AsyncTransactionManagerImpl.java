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
package com.github.joekerouac.async.task.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

import com.github.joekerouac.async.task.exception.NoTransactionException;
import com.github.joekerouac.async.task.function.SqlExecutor;
import com.github.joekerouac.async.task.model.TransStrategy;
import com.github.joekerouac.async.task.spi.AsyncTransactionManager;
import com.github.joekerouac.async.task.spi.ConnectionManager;
import com.github.joekerouac.async.task.spi.TransactionCallback;
import com.github.joekerouac.async.task.spi.TransactionHook;
import com.github.joekerouac.common.tools.constant.ExceptionProviderConst;
import com.github.joekerouac.common.tools.exception.DBException;
import com.github.joekerouac.common.tools.util.Assert;

import lombok.CustomLog;
import lombok.Data;
import lombok.Getter;

/**
 * 默认事务管理器
 * 
 * @author JoeKerouac
 * @date 2023-07-13 10:24
 * @since 3.0.0
 */
@CustomLog
public class AsyncTransactionManagerImpl implements AsyncTransactionManager {

    private final ThreadLocal<ResourceHolder> resourceHolderThreadLocal = new ThreadLocal<>();

    private final ThreadLocal<TransStrategy> transStrategyThreadLocal = new ThreadLocal<>();

    private final ThreadLocal<List<TransactionCallback>> transactionCallbackThreadLocal = new ThreadLocal<>();

    private final ConnectionManager connectionManager;

    private final TransactionHook transactionHook;

    public AsyncTransactionManagerImpl(ConnectionManager connectionManager, TransactionHook transactionHook) {
        Assert.argNotNull(connectionManager);
        this.connectionManager = connectionManager;
        this.transactionHook = transactionHook;
    }

    @Override
    public void runWithTrans(TransStrategy transStrategy, Runnable task) {
        runWithTrans(transStrategy, () -> {
            task.run();
            return null;
        });
    }

    @Override
    public <T> T runWithTrans(TransStrategy transStrategy, Supplier<T> task) {
        Assert.argNotNull(transStrategy, "transStrategy");
        Assert.argNotNull(task, "task");

        TransStrategy oldTransStrategy = transStrategyThreadLocal.get();
        transStrategyThreadLocal.set(transStrategy);
        boolean success = false;
        try {
            T result = task.get();
            success = true;
            return result;
        } finally {
            try {
                transExecFinish(success, true);
            } catch (SQLException e) {
                throw new DBException(e);
            } finally {
                transStrategyThreadLocal.set(oldTransStrategy);
            }
        }
    }

    @Override
    public <T> T run(String requestId, SqlExecutor<T> executor) throws SQLException {
        boolean success = false;
        TransStrategy transStrategy = transStrategyThreadLocal.get();
        try {
            T result = executor
                .execute(decideConnection(transStrategy == null ? TransStrategy.SUPPORTS : transStrategy, requestId));
            success = true;
            return result;
        } finally {
            // 如果transStrategy为空，表示当前没有在事务上下文中执行，直接在这里结束事务即可
            transExecFinish(success, transStrategy == null);
        }
    }

    @Override
    public boolean isActualTransactionActive() {
        ResourceHolder resourceHolder = resourceHolderThreadLocal.get();
        if (resourceHolder != null) {
            try {
                return !resourceHolder.getConnection().getAutoCommit();
            } catch (SQLException sqlException) {
                throw new DBException(sqlException);
            }
        } else if (transactionHook != null) {
            return transactionHook.isActualTransactionActive();
        } else {
            return false;
        }

    }

    @Override
    public void registerCallback(TransactionCallback callback) throws NoTransactionException {
        Assert.assertTrue(isActualTransactionActive(), "当前不在事务中执行，无法注册回调",
            ExceptionProviderConst.IllegalStateExceptionProvider);
        ResourceHolder resourceHolder = resourceHolderThreadLocal.get();
        if (resourceHolder == null || !resourceHolder.isNeedCommit()) {
            // 注意，因为我们校验了当前肯定有事务：
            // 1、如果resourceHolder为空，表示事务不是本管理器管理的，那transactionHook肯定不为空
            // 2、如果resourceHolder不为空，但是事务不需要提交，表示事务也不是本管理器管理的，此时transactionHook可能为空
            Assert.notNull(transactionHook, "当前事务不是异步任务系统管理的，同时没有引入外部事务hook，无法注册事务回调",
                ExceptionProviderConst.IllegalStateExceptionProvider);
            transactionHook.registerCallback(callback);
        } else {
            List<TransactionCallback> transactionCallbacks = transactionCallbackThreadLocal.get();
            if (transactionCallbacks == null) {
                transactionCallbacks = new ArrayList<>();
                transactionCallbackThreadLocal.set(transactionCallbacks);
            }

            transactionCallbacks.add(callback);
        }
    }

    @Override
    public void runAfterCommit(Runnable task) {
        if (isActualTransactionActive()) {
            LOGGER.debug("当前在事务中，等待事务提交后执行");
            registerCallback(new TransactionCallback() {
                @Override
                public void afterCommit() throws RuntimeException {
                    task.run();
                }
            });
        } else {
            LOGGER.debug("当前不在事务中，直接执行");
            task.run();
        }
    }

    /**
     * 决策出我们当前要使用的连接
     * 
     * @param strategy
     *            当前事务策略
     * @param requestId
     *            当前的requestId（分表ID）
     * @return 连接
     * @throws SQLException
     *             SQL异常
     */
    private Connection decideConnection(TransStrategy strategy, String requestId) throws SQLException {
        Connection connection;
        boolean autoCommit;
        ResourceHolder resourceHolder = this.resourceHolderThreadLocal.get();

        switch (strategy) {
            case REQUIRED:
                // 如果当前没有事务，则开启事务
                if (resourceHolder == null) {
                    connection = connectionManager.get(requestId);
                    autoCommit = connection.getAutoCommit();
                    // 如果当前连接是自动提交的（没有事务），则我们需要在执行完毕后提交事务，如果当前连接是非自动提交的（有事务），说明外部已经在管理事务了，我们无需在执行结束后主动提交事务
                    resourceHolder = new ResourceHolder(connection, autoCommit, autoCommit);
                    // 开启事务
                    connection.setAutoCommit(false);

                    this.resourceHolderThreadLocal.set(resourceHolder);
                } else {
                    autoCommit = resourceHolder.getConnection().getAutoCommit();
                    if (autoCommit) {
                        connection = connectionManager.newConnection(requestId);
                        autoCommit = connection.getAutoCommit();
                        // 如果当前连接是自动提交的（没有事务），则我们需要在执行完毕后提交事务，如果当前连接是非自动提交的（有事务），说明外部已经在管理事务了，我们无需在执行结束后主动提交事务
                        resourceHolder = new ResourceHolder(connection, autoCommit, autoCommit, resourceHolder);
                        // 开启事务
                        connection.setAutoCommit(false);

                        this.resourceHolderThreadLocal.set(resourceHolder);
                    }
                }

                break;
            case SUPPORTS:
                if (resourceHolder == null) {
                    connection = connectionManager.get(requestId);
                    autoCommit = connection.getAutoCommit();
                    resourceHolder = new ResourceHolder(connection, autoCommit, false);

                    this.resourceHolderThreadLocal.set(resourceHolder);
                }

                break;
            case MANDATORY:
                if (resourceHolder == null) {
                    // 有可能当前有事务，但是不是本服务管理的，这里仍然尝试一下
                    connection = connectionManager.get(requestId);
                    autoCommit = connection.getAutoCommit();

                    if (autoCommit) {
                        connectionManager.returnConnection(connection);
                        throw new DBException("当前事务执行策略是MANDATORY，但是当前没有事务");
                    }

                    resourceHolder = new ResourceHolder(connection, autoCommit, false);
                    this.resourceHolderThreadLocal.set(resourceHolder);
                } else {
                    connection = resourceHolder.getConnection();
                    autoCommit = connection.getAutoCommit();

                    if (autoCommit) {
                        throw new DBException("当前事务执行策略是MANDATORY，但是当前没有事务");
                    }
                }

                break;
            case REQUIRES_NEW:
                connection = connectionManager.newConnection(requestId);
                autoCommit = connection.getAutoCommit();
                resourceHolder = new ResourceHolder(connection, autoCommit, true, resourceHolder);
                // 开启事务
                connection.setAutoCommit(false);

                this.resourceHolderThreadLocal.set(resourceHolder);
                break;
            case NOT_SUPPORTED:
                autoCommit = true;

                if (resourceHolder != null) {
                    autoCommit = resourceHolder.getConnection().getAutoCommit();
                }

                // 如果当前有事务，则需要归还当前链接，重新获取一个新链接，同时不开启事务；
                if (!autoCommit || resourceHolder == null) {
                    connection = connectionManager.newConnection(requestId);
                    autoCommit = connection.getAutoCommit();
                    resourceHolder = new ResourceHolder(connection, autoCommit, false, resourceHolder);
                    connection.setAutoCommit(true);

                    this.resourceHolderThreadLocal.set(resourceHolder);
                }

                break;
            case NEVER:
                if (resourceHolder == null) {
                    connection = connectionManager.newConnection(requestId);
                    autoCommit = connection.getAutoCommit();
                    resourceHolder = new ResourceHolder(connection, autoCommit, false, null);
                    connection.setAutoCommit(true);
                    this.resourceHolderThreadLocal.set(resourceHolder);
                } else {
                    autoCommit = resourceHolder.getConnection().getAutoCommit();

                    if (!autoCommit) {
                        throw new DBException("当前事务执行策略是NEVER，但是当前存在事务");
                    }
                }

                break;
            default:
                throw new UnsupportedOperationException("不支持的事务策略：" + strategy);
        }

        resourceHolder.ref();

        return resourceHolder.getConnection();
    }

    /**
     * 事务执行完成后的操作
     * 
     * @param success
     *            是否成功
     * @throws SQLException
     *             sql异常
     */
    private void transExecFinish(boolean success, boolean needCommit) throws SQLException {
        ResourceHolder resourceHolder = this.resourceHolderThreadLocal.get();
        if (resourceHolder == null) {
            return;
        }

        resourceHolder.release();

        if (resourceHolder.getRefCount() > 0 || !needCommit) {
            // 可能是嵌套执行
            LOGGER.debug("当前connection引用不为0或者不需要commit，无需释放资源, refCount: [{}], needCommit: [{}]",
                resourceHolder.getRefCount(), needCommit);
            return;
        }

        Connection connection = resourceHolder.getConnection();

        boolean commit = success;

        try {
            if (resourceHolder.isNeedCommit()) {
                List<TransactionCallback> transactionCallbacks = transactionCallbackThreadLocal.get();
                if (transactionCallbacks != null) {
                    transactionCallbacks.sort(Comparator.comparingInt(TransactionCallback::getOrder));
                }
                if (transactionCallbacks != null) {
                    for (TransactionCallback transactionCallback : transactionCallbacks) {
                        try {
                            if (commit) {
                                transactionCallback.beforeCommit(connection.isReadOnly());
                            }
                        } catch (Throwable throwable) {
                            LOGGER.warn(throwable, "事务回调beforeCommit执行失败，回滚事务");
                            commit = false;
                            break;
                        }

                        try {
                            transactionCallback.beforeCompletion();
                        } catch (Throwable throwable) {
                            LOGGER.warn(throwable, "事务回调beforeCompletion执行失败");
                            break;
                        }
                    }
                }

                if (commit) {
                    connection.commit();
                } else {
                    connection.rollback();
                }

                if (transactionCallbacks != null) {
                    for (TransactionCallback transactionCallback : transactionCallbacks) {
                        try {
                            if (commit) {
                                transactionCallback.afterCommit();
                            }
                        } catch (Throwable throwable) {
                            LOGGER.warn(throwable, "事务回调afterCommit执行失败");
                            break;
                        }

                        try {
                            transactionCallback.afterCompletion(
                                commit ? TransactionCallback.STATUS_COMMITTED : TransactionCallback.STATUS_ROLLED_BACK);
                        } catch (Throwable throwable) {
                            LOGGER.warn(throwable, "事务回调afterCompletion执行失败");
                            break;
                        }
                    }
                }
            }

            connection.setAutoCommit(resourceHolder.isAutoCommit());
        } finally {
            this.resourceHolderThreadLocal.set(resourceHolder.getParent());
            connectionManager.returnConnection(connection);
        }
    }

    @Data
    private static class ResourceHolder {

        private final ResourceHolder parent;

        private final Connection connection;

        @Getter
        private final boolean autoCommit;

        @Getter
        private final boolean needCommit;

        @Getter
        private int refCount;

        public ResourceHolder(Connection connection, boolean autoCommit, boolean needCommit) {
            this(connection, autoCommit, needCommit, null);
        }

        public ResourceHolder(Connection connection, boolean autoCommit, boolean needCommit, ResourceHolder parent) {
            this.connection = connection;
            this.autoCommit = autoCommit;
            this.needCommit = needCommit;
            this.refCount = 0;
            this.parent = parent;
        }

        public void ref() {
            refCount += 1;
        }

        public void release() {
            refCount -= 1;
        }

    }

}
