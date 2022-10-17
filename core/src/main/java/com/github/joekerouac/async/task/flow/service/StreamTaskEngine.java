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
package com.github.joekerouac.async.task.flow.service;

import java.sql.SQLException;
import java.util.List;

import com.github.joekerouac.common.tools.constant.ExceptionProviderConst;
import com.github.joekerouac.common.tools.scheduler.SchedulerSystem;
import com.github.joekerouac.common.tools.string.StringUtils;
import com.github.joekerouac.common.tools.util.Assert;
import com.github.joekerouac.async.task.flow.enums.FlowTaskStatus;
import com.github.joekerouac.async.task.flow.enums.TaskNodeStatus;
import com.github.joekerouac.async.task.flow.model.FlowTask;
import com.github.joekerouac.async.task.flow.model.TaskNode;
import com.github.joekerouac.async.task.model.TransStrategy;

import lombok.CustomLog;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
@CustomLog
public class StreamTaskEngine extends AbstractFlowTaskEngine {

    public static final String PROCESSOR_NAME = "StreamTask";

    /**
     * 调度系统
     */
    private final SchedulerSystem schedulerSystem;

    public StreamTaskEngine(EngineConfig config, final SchedulerSystem schedulerSystem) {
        super(config);
        this.schedulerSystem = schedulerSystem;
    }

    @Override
    public String[] processors() {
        return new String[] {PROCESSOR_NAME};
    }

    @Override
    protected List<TaskNode> getAllChild(final TaskNode currentNode) {
        // 先尝试直接获取所有的子节点
        List<TaskNode> allChild = super.getAllChild(currentNode);
        Assert.assertTrue(allChild.size() <= 1, StringUtils.format("数据异常，流式任务应该最多有一个子节点的, [{}:{}]",
            currentNode.getTaskRequestId(), currentNode.getRequestId()), ExceptionProviderConst.DBExceptionProvider);

        // 子节点不为空则直接返回
        if (!allChild.isEmpty()) {
            return allChild;
        }

        try {
            // 子节点为空，加主任务锁再次获取，主任务锁在添加任务的时候和pending的时候也会被锁定（注意，notifyPending的调用和这里不会并发），这里加锁保证不会与其冲突
            return connectionSelector.runWithTrans(currentNode.getRequestId(), TransStrategy.REQUIRED, connection -> {
                // 这里可能会超时，超时抛出异常，我们不用关心，抛出异常重试即可
                FlowTask flowTask = flowTaskRepository.selectForLock(currentNode.getTaskRequestId());
                Assert.notNull(flowTask, StringUtils.format("系统错误，当前子任务 [{}] 对应的主任务 [{}] 不存在",
                    currentNode.getRequestId(), currentNode.getTaskRequestId()),
                    ExceptionProviderConst.DBExceptionProvider);

                List<TaskNode> result = super.getAllChild(currentNode);
                Assert.assertTrue(
                    result.size() <= 1, StringUtils.format("数据异常，流式任务应该最多有一个子节点的, [{}:{}]",
                        currentNode.getTaskRequestId(), currentNode.getRequestId()),
                    ExceptionProviderConst.DBExceptionProvider);

                // 如果任务没有子任务了，尝试立即构建一次，因为可能数据库还有任务是INIT状态还未添加到该任务链上，我们立即执行一次
                if (result.isEmpty()) {
                    schedulerSystem.scheduler(currentNode.getTaskRequestId(), false);
                }
                return result;
            });
        } catch (Throwable e) {
            LOGGER.warn(e, "无限流获取子节点异常，如果频繁出现请关注");
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void taskFinish(final TaskNode currentNode, final TaskNodeStatus taskNodeStatus) {
        // 任务结束不做任何处理，无限流任务没有结束，当前可能只是暂时没有数据了
    }

    @Override
    protected void notifyPending(final TaskNode notifyNode, final TaskNode pendingNode) {
        // 对于流式任务，如果任务节点被通知pending，此时不应该做任何处理，最终状态是父节点异步任务为执行完成，节点任务状态为pending；
        try {
            // 对主任务加锁，加锁后修改主任务状态
            connectionSelector.runWithTrans(notifyNode.getRequestId(), TransStrategy.REQUIRED, connection -> {
                // 这里可能会超时，超时抛出异常，我们不用关心，抛出异常重试即可
                FlowTask flowTask = flowTaskRepository.selectForLock(notifyNode.getTaskRequestId());

                Assert.notNull(flowTask, StringUtils.format("系统错误，当前子任务 [{}] 对应的主任务 [{}] 不存在",
                    notifyNode.getRequestId(), notifyNode.getTaskRequestId()),
                    ExceptionProviderConst.DBExceptionProvider);

                // 幂等校验
                if (flowTask.getStatus() == FlowTaskStatus.PENDING) {
                    return;
                }

                Assert.assertTrue(
                    flowTask.getStatus() == FlowTaskStatus.RUNNING, StringUtils
                        .format("主任务 [{}] 状态为： [{}]，与期望的RUNNING不符", flowTask.getRequestId(), flowTask.getStatus()),
                    ExceptionProviderConst.IllegalStateExceptionProvider);
                // 更新主任务状态pending
                flowTaskRepository.updateStatus(flowTask.getRequestId(), FlowTaskStatus.PENDING);
            });
        } catch (SQLException e) {
            LOGGER.warn(e, "无限流通知pending异常，如果频繁出现请关注");
            throw new RuntimeException(e);
        }

    }
}
