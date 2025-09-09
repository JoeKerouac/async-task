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
package com.github.joekerouac.async.task.flow;

import com.github.joekerouac.async.task.flow.enums.FlowTaskStatus;
import com.github.joekerouac.async.task.flow.enums.TaskNodeStatus;
import com.github.joekerouac.async.task.flow.model.FlowTaskModel;
import com.github.joekerouac.async.task.flow.model.SetTaskModel;
import com.github.joekerouac.async.task.model.ExecStatus;
import com.github.joekerouac.async.task.model.TransStrategy;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Set;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public interface FlowService {

    /**
     * 启动流式服务
     */
    void start();

    /**
     * 关闭流式服务
     */
    void stop();

    /**
     * 添加任务，如果当前存在事务，应该加入事务，如果当前没有事务，则创建事务并在事务中运行
     * 
     * @param task
     *            待添加的任务，不允许为null
     * @throws IllegalArgumentException
     *             如果要添加的任务为空则抛出该异常
     * @throws IllegalStateException
     *             如果当前服务未启动则抛出该异常
     */
    void addTask(@NotNull FlowTaskModel task) throws IllegalArgumentException, IllegalStateException;

    /**
     * 结束指定流，后续往该流添加的任务将无法被调度
     * 
     * @param streamId
     *            流ID
     */
    void finishStream(String streamId);

    /**
     * 根据requestId查询流式任务的状态
     * 
     * @param requestId
     *            requestId
     * @return 流式任务的状态，如果没有找到指定的流式任务则返回空；
     * @throws IllegalArgumentException
     *             如果传入的requestId参数为空则抛出该异常
     * @throws IllegalStateException
     *             如果当前服务未启动则抛出该异常
     */
    FlowTaskStatus queryTaskStatus(@NotBlank String requestId) throws IllegalArgumentException, IllegalStateException;

    /**
     * 获取有限集任务
     * 
     * @param requestId
     *            requestId
     * @return 有限集任务
     * @throws IllegalArgumentException
     *             如果传入的requestId参数为空或者任务类型不是有限集任务则抛出该异常
     * @throws IllegalStateException
     *             如果当前服务未启动则抛出该异常
     */
    SetTaskModel getSetTaskModel(@NotBlank String requestId) throws IllegalArgumentException, IllegalStateException;

    /**
     * 根据任务节点的requestId查询任务节点状态
     * 
     * @param nodeRequestId
     *            任务节点
     * @return 节点状态，如果没有找到指定的任务节点则返回空；
     * @throws IllegalArgumentException
     *             如果传入的nodeRequestId为空则抛出该异常
     * @throws IllegalStateException
     *             如果当前服务未启动则抛出该异常
     */
    TaskNodeStatus queryNodeStatus(@NotBlank String nodeRequestId)
        throws IllegalArgumentException, IllegalStateException;

    /**
     * 唤醒任务，如果任务处于{@link ExecStatus#WAIT}状态，则任务被唤醒，切换到{@link ExecStatus#READY}状态，如果当前存在事务，应该加入事务，如果当前没有事务，则不使用事务
     *
     * @param requestId
     *            任务requestId
     * @return true表示唤醒成功，false表示任务不存在、任务状态不是wait等导致唤醒失败
     */
    default boolean notifyNode(String requestId) {
        return notifyNode(requestId, TransStrategy.SUPPORTS);
    }

    /**
     * 唤醒任务，如果任务处于{@link ExecStatus#WAIT}状态，则任务被唤醒，切换到{@link ExecStatus#READY}状态
     *
     * @param requestId
     *            任务requestId
     * @param transStrategy
     *            事务策略
     * @return true表示唤醒成功，false表示任务不存在、任务状态不是wait等导致唤醒失败
     */
    boolean notifyNode(String requestId, TransStrategy transStrategy);

    /**
     * 批量唤醒任务，如果任务处于{@link ExecStatus#WAIT}状态，则任务被唤醒，切换到{@link ExecStatus#READY}状态；
     *
     * @param requestIdSet
     *            任务request id集合
     * @return 唤醒成功的任务request id集合，其他任务可能因为状态不是wait、任务不存在等导致不会被唤醒
     */
    default Set<String> notifyNode(Set<String> requestIdSet) {
        return notifyNode(requestIdSet, TransStrategy.REQUIRED);
    }

    /**
     * 批量唤醒任务，如果任务处于{@link ExecStatus#WAIT}状态，则任务被唤醒，切换到{@link ExecStatus#READY}状态；
     *
     * @param requestIdSet
     *            任务request id集合
     * @param transStrategy
     *            事务策略，当传入的request id集合数量大于1时，下仅支持{@link TransStrategy#REQUIRED}和{@link TransStrategy#REQUIRES_NEW}两种策略
     * @return 唤醒成功的任务request id集合，其他任务可能因为状态不是wait、任务不存在等导致不会被唤醒
     */
    Set<String> notifyNode(Set<String> requestIdSet, TransStrategy transStrategy);

}
