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
package com.github.joekerouac.async.task.service;

import com.github.joekerouac.async.task.model.TransStrategy;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public class TransactionSynchronizationManager {

    private static final ThreadLocal<TransStrategy> JOIN_TRANSACTION_FLAG = new ThreadLocal<>();

    /**
     * 当前线程的for update支持设置
     */
    private static final ThreadLocal<Boolean> LOCAL_SUPPORT_SELECT_FOR_UPDATE = new ThreadLocal<>();

    /**
     * 全局设置，当前系统是否支持select for update语句，默认支持，在某些数据库下可能不支持，例如sqlite，此时应该设置为false；
     */
    private static volatile boolean GLOBAL_SUPPORT_SELECT_FOR_UPDATE = true;

    /**
     * 设置当前线程的for update语句支持配置
     * 
     * @param supportSelectForUpdate
     *            当前线程的for update语句支持配置
     */
    public static void setLocalSupportSelectForUpdate(boolean supportSelectForUpdate) {
        LOCAL_SUPPORT_SELECT_FOR_UPDATE.set(supportSelectForUpdate);
    }

    /**
     * 清空当前线程设置的for update语句支持
     */
    public static void clearLocalSupportSelectForUpdate() {
        LOCAL_SUPPORT_SELECT_FOR_UPDATE.remove();
    }

    /**
     * 获取当前线程的for update语句支持设置
     * 
     * @return 为null表示当前线程没有设置
     */
    public static Boolean getLocalSupportSelectForUpdate() {
        return LOCAL_SUPPORT_SELECT_FOR_UPDATE.get();
    }

    /**
     * 全局设置for update语句支持
     * 
     * @param supportSelectForUpdate
     *            true表示支持for update语句
     */
    public static void setGlobalSupportSelectForUpdate(boolean supportSelectForUpdate) {
        GLOBAL_SUPPORT_SELECT_FOR_UPDATE = supportSelectForUpdate;
    }

    /**
     * 获取当前系统是否支持for update语句
     * 
     * @return true表示支持for update语句
     */
    public static boolean getSupportSelectForUpdate() {
        Boolean local = LOCAL_SUPPORT_SELECT_FOR_UPDATE.get();
        return local == null ? GLOBAL_SUPPORT_SELECT_FOR_UPDATE : local;
    }

    /**
     * 设置当前事务处理逻辑
     * 
     * @param transStrategy
     *            事务处理逻辑
     */
    public static void setTransStrategy(TransStrategy transStrategy) {
        JOIN_TRANSACTION_FLAG.set(transStrategy);
    }

    /**
     * 获取当前事务处理逻辑
     * 
     * @return 当前事务处理逻辑
     */
    public static TransStrategy getTransStrategy() {
        return JOIN_TRANSACTION_FLAG.get();
    }

    /**
     * 清除事务处理逻辑标记
     */
    public static void clearTransStrategy() {
        JOIN_TRANSACTION_FLAG.remove();
    }

}
