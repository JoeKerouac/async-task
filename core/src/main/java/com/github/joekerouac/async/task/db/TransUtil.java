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

import java.util.function.Supplier;

import com.github.joekerouac.async.task.model.TransStrategy;
import com.github.joekerouac.async.task.service.TransactionSynchronizationManager;

/**
 * 事务工具
 *
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public class TransUtil {

    /**
     * 使用新的事务策略执行事务任务，执行完毕后恢复之前的事务策略
     * 
     * @param strategy
     *            新的事务策略
     * @param task
     *            事务任务
     */
    public static void run(TransStrategy strategy, Runnable task) {
        TransStrategy oldTransStrategy = TransactionSynchronizationManager.getTransStrategy();
        TransactionSynchronizationManager.setTransStrategy(strategy);
        try {
            task.run();
        } finally {
            if (oldTransStrategy != null) {
                TransactionSynchronizationManager.setTransStrategy(oldTransStrategy);
            } else {
                TransactionSynchronizationManager.clearTransStrategy();
            }
        }
    }

    /**
     * 使用新的事务策略运行一个带有结果的任务
     * 
     * @param strategy
     *            事务策略
     * @param task
     *            任务
     * @param <T>
     *            任务结果类型
     * @return 任务结果
     */
    public static <T> T run(TransStrategy strategy, Supplier<T> task) {
        TransStrategy oldTransStrategy = TransactionSynchronizationManager.getTransStrategy();
        TransactionSynchronizationManager.setTransStrategy(strategy);
        try {
            return task.get();
        } finally {
            if (oldTransStrategy != null) {
                TransactionSynchronizationManager.setTransStrategy(oldTransStrategy);
            } else {
                TransactionSynchronizationManager.clearTransStrategy();
            }
        }
    }

}
