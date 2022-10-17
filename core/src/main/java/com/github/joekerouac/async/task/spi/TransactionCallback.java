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

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public interface TransactionCallback {

    /** 事务已提交 */
    int STATUS_COMMITTED = 0;

    /** 事务已回滚 */
    int STATUS_ROLLED_BACK = 1;

    /** 系统错误 */
    int STATUS_UNKNOWN = 2;

    /**
     * 该回调的优先级，数值越大优先级越低
     */
    default int getOrder() {
        return Integer.MAX_VALUE;
    }

    /**
     * 将数据从缓存刷新到db，例如在hibernate JPA场景下会有用
     */
    default void flush() {}

    /**
     * 将事务提交前调用该回调，但是并不意味着事务一定会提交并且提交成功，例如提交时出现了网络异常或者sql错误导致回滚等；
     * 
     * @param readOnly
     *            事务是否是只读的
     * @throws RuntimeException
     *             抛出异常时异常会传播给调用者，将可能影响事务
     */
    default void beforeCommit(boolean readOnly) throws RuntimeException {}

    /**
     * 事务完成前回调（可能是正常结束提交了，也可能是回滚了）
     * 
     * @throws RuntimeException
     *             抛出运行时异常时异常会被记录但是不会传播，也就是事务不会受影响
     */
    default void beforeCompletion() throws RuntimeException {}

    /**
     * 事务提交后调用，可以在事务成功提交后立即执行进一步的操作，注意，此时虽然事务已经提交了，但是事务资源仍可能处于活动状态，因此，此时触发的任何数据访问代码仍将
     * 参与原始事务，允许执行一些清理（不在有提交），除非明确声明他需要在单独的事务中运行，因此，对于从这里调用的任务事务操作都会新建事务；
     * 
     * @throws RuntimeException
     *             抛出异常时异常会传播给调用者，不会影响事务
     */
    default void afterCommit() throws RuntimeException {}

    /**
     * 事务完成后调用，可以在事务完成后立即执行进一步的操作，注意，此时虽然事务已经完成了，但是事务资源仍可能处于活动状态，因此，此时触发的任何数据访问代码仍将
     * 参与原始事务，允许执行一些清理（不在有提交），除非明确声明他需要在单独的事务中运行，因此，对于从这里调用的任务事务操作都会新建事务；
     * 
     * @param status
     *            事务状态，参考STATUS_*变量
     * @throws RuntimeException
     *             抛出运行时异常时异常会被记录但是不会传播，也就是事务不会受影响
     * @see #STATUS_COMMITTED
     * @see #STATUS_ROLLED_BACK
     * @see #STATUS_UNKNOWN
     * @see #beforeCompletion
     */
    default void afterCompletion(int status) {}

}
