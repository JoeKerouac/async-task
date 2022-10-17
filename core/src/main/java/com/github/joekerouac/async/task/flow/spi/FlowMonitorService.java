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
package com.github.joekerouac.async.task.flow.spi;

import com.github.joekerouac.async.task.flow.enums.TaskNodeStatus;
import com.github.joekerouac.async.task.flow.enums.FlowTaskStatus;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public interface FlowMonitorService {

    /**
     * 任务反序列化异常
     *
     * @param requestId
     *            task node创建时的幂等请求ID
     * @param task
     *            序列化后的任务
     * @param processor
     *            任务处理器
     * @param throwable
     *            异常详情
     */
    default void deserializationError(String requestId, String task, Object processor, Throwable throwable) {

    }

    /**
     * 节点状态断言异常，节点状态不符合我们的期望，通常是由于执行策略问题导致的，或者是人工修改数据
     * 
     * @param nodeRequestId
     *            节点的reuqestId
     * @param expectStatus
     *            节点期望状态
     * @param realStatus
     *            节点实际状态
     */
    default void nodeStatusAssertError(String nodeRequestId, TaskNodeStatus expectStatus, TaskNodeStatus realStatus) {

    }

    /**
     * 节点执行完毕
     * 
     * @param taskRequestId
     *            节点对应的主任务的request id
     * @param nodeRequestId
     *            节点request id
     * @param status
     *            节点状态
     */
    default void nodeFinish(String taskRequestId, String nodeRequestId, TaskNodeStatus status) {

    }

    /**
     * 任务执行完毕
     * 
     * @param taskRequestId
     *            任务requestId
     * @param status
     *            任务最终状态
     */
    default void taskFinish(String taskRequestId, FlowTaskStatus status) {

    }

    /**
     * task node找不到异常，该异常是严重异常，大概率是数据被删除了，请监控；
     * 
     * @param asyncTaskRequestId
     *            异步任务的requestId
     * @param nodeRequestId
     *            task node的request id
     */
    default void nodeNotFound(String asyncTaskRequestId, String nodeRequestId) {

    }

    /**
     * 指定处理器找不到
     * 
     * @param nodeRequestId
     *            任务节点requestId
     * @param processor
     *            对应的处理器名
     */
    default void processorNotFound(String nodeRequestId, String processor) {

    }

}
