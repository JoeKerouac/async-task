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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import com.github.joekerouac.async.task.exception.DBException;
import com.github.joekerouac.async.task.exception.NoTransactionException;
import com.github.joekerouac.async.task.function.SqlExecutor;
import com.github.joekerouac.async.task.model.TransStrategy;
import com.github.joekerouac.async.task.spi.AsyncTransactionManager;
import com.github.joekerouac.async.task.spi.ConnectionManager;
import com.github.joekerouac.async.task.spi.TransactionCallback;
import com.github.joekerouac.async.task.spi.TransactionHook;
import com.github.joekerouac.common.tools.constant.ExceptionProviderConst;
import com.github.joekerouac.common.tools.string.StringUtils;
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

    private final ThreadLocal<ConnectionContext> connContextThreadLocal = new ThreadLocal<>();

    private final ConnectionManager connectionManager;

    private final TransactionHook transactionHook;

    private final AtomicLong transIdGen = new AtomicLong();

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

        if (transStrategy == TransStrategy.NEVER) {
            if (isActualTransactionActive()) {
                throw new DBException("当前事务策略是NEVER, 但是当前在事务上下文中");
            }
        }

        if (transStrategy == TransStrategy.MANDATORY) {
            if (!isActualTransactionActive()) {
                throw new DBException("当前事务策略是MANDATORY, 但是当前不在事务上下文中");
            }
        }

        ConnectionContext oldContext = connContextThreadLocal.get();
        ConnectionContext current =
            new ConnectionContext(oldContext, transIdGen.incrementAndGet(), transStrategy, null);
        connContextThreadLocal.set(current);

        try {
            getConnection(null);
        } catch (Throwable throwable) {
            connContextThreadLocal.set(oldContext);
            throw new DBException("连接初始失败", throwable);
        }

        boolean success = false;
        try {
            T result = task.get();
            success = true;
            return result;
        } finally {
            try {
                transExecFinish(success);
            } catch (SQLException e) {
                throw new DBException(e);
            } finally {
                connContextThreadLocal.set(oldContext);
            }
        }
    }

    @Override
    public <T> T run(String requestId, SqlExecutor<T> executor) throws SQLException {
        boolean success = false;
        Connection connection = getConnection(requestId);
        try {
            T result = executor.execute(connection);
            success = true;
            return result;
        } finally {
            transExecFinish(success);
        }
    }

    @Override
    public boolean isActualTransactionActive() {
        ConnectionContext context = connContextThreadLocal.get();

        while (context != null) {
            TransStrategy transStrategy = context.getTransStrategy();
            // 如果当前事务策略是NEVER或者NOT_SUPPORTED，说明当前肯定没有事务
            if (transStrategy == TransStrategy.NEVER || transStrategy == TransStrategy.NOT_SUPPORTED) {
                return false;
            }

            if (transStrategy == TransStrategy.REQUIRED || transStrategy == TransStrategy.REQUIRES_NEW
                || transStrategy == TransStrategy.MANDATORY) {
                return true;
            }

            context = context.getParent();
        }

        return transactionHook != null && transactionHook.isActualTransactionActive();
    }

    @Override
    public void registerCallback(TransactionCallback callback) throws NoTransactionException {
        Assert.assertTrue(isActualTransactionActive(), "当前不在事务中执行，无法注册回调",
            ExceptionProviderConst.IllegalStateExceptionProvider);
        ConnectionContext context = connContextThreadLocal.get();

        if (context == null) {
            // 注意，因为我们校验了当前肯定有事务：
            // 1、如果resourceHolder为空，表示事务不是本管理器管理的，那transactionHook肯定不为空
            // 2、如果resourceHolder不为空，但是事务不需要提交，表示事务也不是本管理器管理的，此时transactionHook可能为空
            Assert.notNull(transactionHook, "当前事务不是异步任务系统管理的，同时没有引入外部事务hook，无法注册事务回调",
                ExceptionProviderConst.IllegalStateExceptionProvider);
            transactionHook.registerCallback(callback);
        } else {
            context = getCurrentConn(context, null, context.getTransStrategy());
            List<TransactionCallback> transactionCallbacks = context.getTransactionCallbacks();
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
     * 获取当前事务的连接
     * 
     * @param requestId
     *            当前的requestId（分表ID）
     * @return 连接
     * @throws SQLException
     *             SQL异常
     */
    private Connection getConnection(String requestId) throws SQLException {
        ConnectionContext currentContext = connContextThreadLocal.get();
        boolean newContext = false;
        if (currentContext == null) {
            currentContext = new ConnectionContext(null, transIdGen.incrementAndGet(), TransStrategy.DEFAULT, null);
            connContextThreadLocal.set(currentContext);
            newContext = true;
        }

        ResourceHolder resourceHolder = currentContext.getResourceHolder();
        if (resourceHolder != null) {
            resourceHolder.ref();
            return resourceHolder.getConnection();
        }

        // 获取实际要使用的conn
        ConnectionContext usedContext = getCurrentConn(currentContext, null, currentContext.getTransStrategy());
        resourceHolder = usedContext.getResourceHolder();

        if (resourceHolder != null) {
            resourceHolder.ref();
            currentContext.setResourceHolder(resourceHolder);
            return resourceHolder.getConnection();
        }

        TransStrategy strategy = usedContext.getTransStrategy();

        Connection connection = null;
        boolean autoCommit;

        try {
            switch (strategy) {
                case REQUIRED:
                    // 如果当前没有事务，则开启事务
                    connection = connectionManager.get(requestId);
                    autoCommit = connection.getAutoCommit();
                    // 如果当前连接是自动提交的（没有事务），则我们需要在执行完毕后提交事务，如果当前连接是非自动提交的（有事
                    // 务），说明外部已经在管理事务了，我们无需在执行结束后主动提交事务
                    resourceHolder = new ResourceHolder(usedContext.getTid(), connection, autoCommit, autoCommit);

                    if (autoCommit) {
                        // 开启事务
                        connection.setAutoCommit(false);
                    }

                    break;
                case SUPPORTS:
                    connection = connectionManager.get(requestId);
                    autoCommit = connection.getAutoCommit();
                    resourceHolder = new ResourceHolder(usedContext.getTid(), connection, autoCommit, false);

                    break;
                case MANDATORY:
                    // 有可能当前有事务，但是不是本服务管理的，这里仍然尝试一下
                    connection = connectionManager.get(requestId);
                    autoCommit = connection.getAutoCommit();

                    if (autoCommit) {
                        throw new DBException("当前事务执行策略是MANDATORY，但是当前没有事务");
                    }

                    resourceHolder = new ResourceHolder(usedContext.getTid(), connection, false, false);

                    break;
                case REQUIRES_NEW:
                    connection = connectionManager.get(requestId);
                    autoCommit = connection.getAutoCommit();

                    // 如果当前已经有事务，则重新构建一个链接开启事务
                    if (!autoCommit) {
                        connectionManager.returnConnection(connection);
                        connection = connectionManager.newConnection(requestId);
                        autoCommit = connection.getAutoCommit();
                        // 如果新建链接没有事务，则开启事务，理论上新建连接都应该没有事务的
                        if (autoCommit) {
                            connection.setAutoCommit(false);
                        } else {
                            throw new DBException(StringUtils.format("当前新建连接存在事务, [{}]", connection));
                        }
                    }

                    resourceHolder = new ResourceHolder(usedContext.getTid(), connection, true, true);
                    break;
                case NOT_SUPPORTED:
                    connection = connectionManager.get(requestId);
                    autoCommit = connection.getAutoCommit();

                    if (!autoCommit) {
                        connectionManager.returnConnection(connection);
                        connection = connectionManager.newConnection(requestId);
                        autoCommit = connection.getAutoCommit();

                        if (!autoCommit) {
                            throw new DBException(StringUtils.format("当前新建连接存在事务, [{}]", connection));
                        }
                    }

                    resourceHolder = new ResourceHolder(usedContext.getTid(), connection, true, false);

                    break;
                case NEVER:
                    connection = connectionManager.get(requestId);
                    autoCommit = connection.getAutoCommit();

                    if (!autoCommit) {
                        throw new DBException("当前事务执行策略是NEVER，但是当前有事务");
                    }

                    resourceHolder = new ResourceHolder(usedContext.getTid(), connection, true, false);

                    break;
                default:
                    throw new UnsupportedOperationException("不支持的事务策略：" + strategy);
            }
        } catch (RuntimeException | SQLException e) {
            if (newContext) {
                connContextThreadLocal.remove();
            }

            if (connection != null) {
                connectionManager.returnConnection(connection);
            }
            throw e;
        }

        usedContext.setResourceHolder(resourceHolder);
        if (currentContext != usedContext) {
            currentContext.setResourceHolder(resourceHolder);
        }

        resourceHolder.ref();
        return resourceHolder.getConnection();
    }

    /**
     * 获取当前应该使用的链接
     *
     * @param context
     *            当前的ConnectionContext
     * @param candidate
     *            备选ConnectionContext
     * @param current
     *            当前事务策略
     * @return 当前应该使用的链接，不为空
     */
    private ConnectionContext getCurrentConn(ConnectionContext context, ConnectionContext candidate,
        TransStrategy current) {
        if (context == null) {
            return candidate;
        }

        if (current == TransStrategy.REQUIRES_NEW) {
            return context;
        }

        // 如果当前是NEVER，那往上追溯肯定也都是不需要事务的，直接递归获取最上层链接即可
        if (current == TransStrategy.NEVER) {
            return getCurrentConn(context.getParent(), context, current);
        }

        TransStrategy currentTransStrategy = context.getTransStrategy();
        // 如果是REQUIRED或者MANDATORY，当前肯定是需要事务的
        if (current == TransStrategy.REQUIRED || current == TransStrategy.MANDATORY) {
            if (currentTransStrategy == TransStrategy.REQUIRED || currentTransStrategy == TransStrategy.MANDATORY) {
                return getCurrentConn(context.getParent(), context, current);
            } else if (currentTransStrategy == TransStrategy.REQUIRES_NEW
                || currentTransStrategy == TransStrategy.NOT_SUPPORTED || currentTransStrategy == TransStrategy.NEVER) {
                return candidate;
            } else if (currentTransStrategy == TransStrategy.SUPPORTS) {
                ConnectionContext parent = context.getParent();
                if (parent == null && transactionHook != null && transactionHook.isActualTransactionActive()) {
                    return context;
                } else {
                    return getCurrentConn(context.getParent(), candidate, current);
                }
            }
        }

        if (current == TransStrategy.SUPPORTS) {
            if (currentTransStrategy == TransStrategy.REQUIRED || currentTransStrategy == TransStrategy.SUPPORTS
                || currentTransStrategy == TransStrategy.NEVER || currentTransStrategy == TransStrategy.NOT_SUPPORTED
                || currentTransStrategy == TransStrategy.MANDATORY) {
                return getCurrentConn(context.getParent(), context, currentTransStrategy);
            } else if (currentTransStrategy == TransStrategy.REQUIRES_NEW) {
                return context;
            }
        }

        if (current == TransStrategy.NOT_SUPPORTED) {
            if (currentTransStrategy == TransStrategy.REQUIRED || currentTransStrategy == TransStrategy.MANDATORY
                || currentTransStrategy == TransStrategy.REQUIRES_NEW) {
                return candidate;
            } else if (currentTransStrategy == TransStrategy.NEVER
                || currentTransStrategy == TransStrategy.NOT_SUPPORTED) {
                return getCurrentConn(context.getParent(), context, current);
            } else if (currentTransStrategy == TransStrategy.SUPPORTS) {
                ConnectionContext parent = context.getParent();
                if (parent == null && transactionHook != null && !transactionHook.isActualTransactionActive()) {
                    return context;
                } else {
                    return getCurrentConn(context.getParent(), candidate, current);
                }
            }
        }

        throw new DBException(StringUtils.format("未知的事务策略: [{}]", current));
    }

    /**
     * 事务执行完成后的操作
     *
     * @param success
     *            是否成功
     * @throws SQLException
     *             sql异常
     */
    private void transExecFinish(boolean success) throws SQLException {
        ConnectionContext context = connContextThreadLocal.get();
        if (context == null) {
            return;
        }

        ResourceHolder resourceHolder = context.getResourceHolder();
        // 理论上这里肯定不为空
        if (resourceHolder == null) {
            throw new DBException("当前resourceHolder为null");
        }

        resourceHolder.release();

        boolean needRelease = resourceHolder.getTid() == context.getTid() && resourceHolder.getRefCount() == 0;
        boolean needCommit = needRelease && resourceHolder.isNeedCommit();

        if (!needCommit) {
            // 可能是嵌套执行
            LOGGER.debug("当前connection无需释放或者无需主动提交事务");

            if (needRelease) {
                // 事务不是本管理器管理的，无需提交事务，只需要释放链接资源即可
                releaseResource(context);

                // 如果当前引用已经没有了，但是不需要提交，说明事务是在上层管理的，这里将事务回调注册到上层执行
                List<TransactionCallback> transactionCallbacks = context.getTransactionCallbacks();
                if (!transactionCallbacks.isEmpty()) {
                    transactionCallbacks.sort(Comparator.comparingInt(TransactionCallback::getOrder));
                    // 如果当前有事务回调，并且当前事务是外部管理的，transactionHook不能为空
                    Assert.notNull(transactionHook, "当前事务不是异步任务系统管理的，同时没有引入外部事务hook，无法注册事务回调",
                        ExceptionProviderConst.IllegalStateExceptionProvider);
                    for (TransactionCallback transactionCallback : context.getTransactionCallbacks()) {
                        transactionHook.registerCallback(transactionCallback);
                    }
                }
            }

            return;
        }

        Connection connection = resourceHolder.getConnection();

        boolean commit = success;
        boolean release = false;
        try {
            List<TransactionCallback> transactionCallbacks = context.getTransactionCallbacks();
            transactionCallbacks.sort(Comparator.comparingInt(TransactionCallback::getOrder));
            for (TransactionCallback transactionCallback : transactionCallbacks) {
                try {
                    if (commit) {
                        transactionCallback.beforeCommit(connection.isReadOnly());
                    }
                } catch (Throwable throwable) {
                    LOGGER.error(throwable, "事务回调beforeCommit执行失败，回滚事务");
                    commit = false;
                    break;
                }

                try {
                    transactionCallback.beforeCompletion();
                } catch (Throwable throwable) {
                    LOGGER.error(throwable, "事务回调beforeCompletion执行失败");
                    break;
                }
            }

            if (commit) {
                connection.commit();
            } else {
                connection.rollback();
            }

            // 事务结束，清理资源
            releaseResource(context);
            release = true;

            for (TransactionCallback transactionCallback : transactionCallbacks) {
                try {
                    if (commit) {
                        transactionCallback.afterCommit();
                    }
                } catch (Throwable throwable) {
                    LOGGER.error(throwable, "事务回调afterCommit执行失败");
                    break;
                }

                try {
                    transactionCallback.afterCompletion(
                        commit ? TransactionCallback.STATUS_COMMITTED : TransactionCallback.STATUS_ROLLED_BACK);
                } catch (Throwable throwable) {
                    LOGGER.error(throwable, "事务回调afterCompletion执行失败");
                    break;
                }
            }
        } finally {
            if (!release) {
                releaseResource(context);
            }
        }
    }

    /**
     * 释放链接资源
     *
     * @param context
     *            链接上下文
     * @throws SQLException
     *             sql异常
     */
    private void releaseResource(ConnectionContext context) throws SQLException {
        ResourceHolder resourceHolder = context.getResourceHolder();
        if (resourceHolder == null) {
            return;
        }

        Connection connection = resourceHolder.getConnection();
        try {
            boolean autoCommit = connection.getAutoCommit();
            if (autoCommit != resourceHolder.isAutoCommit()) {
                connection.setAutoCommit(resourceHolder.isAutoCommit());
            }
        } finally {
            // 提前将上下文更新，防止后边的回调中重复使用
            connContextThreadLocal.set(context.getParent());
            connectionManager.returnConnection(connection);
        }
    }

    @Data
    private static class ResourceHolder {

        private final long tid;

        private final Connection connection;

        @Getter
        private final boolean autoCommit;

        @Getter
        private final boolean needCommit;

        private int refCount;

        public ResourceHolder(long tid, Connection connection, boolean autoCommit, boolean needCommit) {
            this.tid = tid;
            this.connection = connection;
            this.autoCommit = autoCommit;
            this.needCommit = needCommit;
            this.refCount = 0;
        }

        public void ref() {
            refCount += 1;
        }

        public void release() {
            refCount -= 1;
        }

    }

    @Data
    private static class ConnectionContext {

        /**
         * 父节点
         */
        private ConnectionContext parent;

        /**
         * 当前事务ID
         */
        private long tid;

        /**
         * 当前事务策略
         */
        private TransStrategy transStrategy;

        /**
         * 当前事务的resource
         */
        private ResourceHolder resourceHolder;

        /**
         * 事务回调
         */
        private List<TransactionCallback> transactionCallbacks;

        public ConnectionContext(ConnectionContext parent, long tid, TransStrategy transStrategy,
            ResourceHolder resourceHolder) {
            this.parent = parent;
            this.tid = tid;
            this.transStrategy = transStrategy;
            this.resourceHolder = resourceHolder;
            this.transactionCallbacks = new ArrayList<>();
        }
    }

}
