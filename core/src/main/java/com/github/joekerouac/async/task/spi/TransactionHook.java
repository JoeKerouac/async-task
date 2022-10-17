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

import com.github.joekerouac.async.task.exception.NoTransactionException;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public interface TransactionHook {

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
