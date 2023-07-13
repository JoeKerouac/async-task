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

import java.sql.SQLException;
import java.util.function.Supplier;

import javax.validation.constraints.NotNull;

import com.github.joekerouac.async.task.exception.NoTransactionException;
import com.github.joekerouac.async.task.function.SqlExecutor;
import com.github.joekerouac.async.task.model.TransStrategy;

/**
 * 异步任务系统内部事务管理器
 * 
 * @author JoeKerouac
 * @date 2023-07-13 14:56
 * @since 3.0.0
 */
public interface AsyncTransactionManager {

    /**
     * 以指定策略执行事务
     * 
     * @param transStrategy
     *            事务策略
     * @param task
     *            要在指定事务策略内执行的任务
     */
    void runWithTrans(@NotNull TransStrategy transStrategy, Runnable task);

    /**
     * 以指定策略执行事务
     *
     * @param transStrategy
     *            事务策略
     * @param task
     *            要在指定事务策略内执行的任务
     * @param <T>
     *            执行结果类型
     * @return 执行结果
     */
    <T> T runWithTrans(@NotNull TransStrategy transStrategy, Supplier<T> task);

    /**
     * 在当前事务上下文中执行sql，如果当前没有事务上下文则不开启事务执行
     * 
     * @param requestId
     *            requestId，如果存在分表时使用
     * @param executor
     *            sql执行器
     * @param <T>
     *            执行结果类型
     * @return 执行结果
     * @throws SQLException
     *             SQL异常
     */
    <T> T run(String requestId, SqlExecutor<T> executor) throws SQLException;

    /**
     * 当前线程是否处在事务中
     *
     * @return true表示当前线程已经处在事务中了
     */
    boolean isActualTransactionActive();

    /**
     * 注册事务回调，如果当前没有在事务中则会抛出异常
     *
     * @param callback
     *            事务回调
     * @throws NoTransactionException
     *             当前没有事务时抛出该异常
     */
    void registerCallback(TransactionCallback callback) throws NoTransactionException;

}
