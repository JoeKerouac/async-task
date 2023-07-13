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

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.github.joekerouac.async.task.exception.NoTransactionException;
import com.github.joekerouac.async.task.spi.TransactionCallback;
import com.github.joekerouac.async.task.spi.TransactionHook;

/**
 * 依赖spring-jdbc实现的transaction hook
 *
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public class SpringJdbcTransactionHook implements TransactionHook {

    @Override
    public boolean isActualTransactionActive() {
        return TransactionSynchronizationManager.isActualTransactionActive();
    }

    @Override
    public void registerCallback(final TransactionCallback callback) {
        if (!isActualTransactionActive()) {
            throw new NoTransactionException("当前没有在事务中，无法注册事务回调；" + "PS：注册前可以使用"
                + "com.github.joekerouac.async.task.spi.TransactionManager.isActualTransactionActive" + "判断当前是否在事务中");
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void beforeCommit(final boolean readOnly) {
                callback.beforeCommit(readOnly);
            }

            @Override
            public void beforeCompletion() {
                callback.beforeCompletion();
            }

            @Override
            public void afterCommit() {
                callback.afterCommit();
            }

            @Override
            public void afterCompletion(final int status) {
                callback.afterCompletion(status);
            }
        });
    }

}
