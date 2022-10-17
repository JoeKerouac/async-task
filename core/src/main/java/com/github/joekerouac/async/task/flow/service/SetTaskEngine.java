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

import com.github.joekerouac.common.tools.string.StringUtils;
import com.github.joekerouac.async.task.flow.enums.FlowTaskStatus;
import com.github.joekerouac.async.task.flow.enums.TaskNodeStatus;
import com.github.joekerouac.async.task.flow.model.TaskNode;

import lombok.CustomLog;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
@CustomLog
public class SetTaskEngine extends AbstractFlowTaskEngine {

    public static final String PROCESSOR_NAME = "SetTask";

    public SetTaskEngine(EngineConfig config) {
        super(config);
    }

    @Override
    public String[] processors() {
        return new String[] {PROCESSOR_NAME};
    }

    @Override
    protected void taskFinish(final TaskNode currentNode, final TaskNodeStatus taskNodeStatus) {
        FlowTaskStatus flowTaskStatus;
        switch (taskNodeStatus) {
            case SUCCESS:
            case ERROR:
                flowTaskStatus = FlowTaskStatus.FINISH;
                break;
            case PENDING:
                flowTaskStatus = FlowTaskStatus.PENDING;
                break;
            default:
                throw new IllegalStateException(StringUtils.format("不支持的尾节点状态： [{}]", taskNodeStatus));
        }

        LOGGER.info("当前节点 [{}] 是尾节点， 执行状态为： [{}]， 任务 [{}] 执行完毕，任务最终状态为： [{}]", currentNode.getRequestId(),
            taskNodeStatus, currentNode.getTaskRequestId(), flowTaskStatus);

        // 只有任务开始的时候和任务结束的时候才会更新状态，任务开始和任务结束肯定不会并发，任务又只有一个尾节点，所以同一时间只会有一个任务
        // 结束调用，也不会并发；
        flowTaskRepository.updateStatus(currentNode.getTaskRequestId(), flowTaskStatus);
        flowMonitorService.taskFinish(currentNode.getTaskRequestId(), flowTaskStatus);
    }

    @Override
    protected void notifyPending(final TaskNode notifyNode, final TaskNode pendingNode) {
        // 将任务节点设置为PENDING，然后唤醒该节点，然后执行的时候会发现节点是pending状态，会直接进入
        int updateResult = taskNodeRepository.casUpdateStatus(pendingNode.getTaskRequestId(), pendingNode.getStatus(),
            TaskNodeStatus.PENDING);
        // 如果更新失败，则从内存刷新
        if (updateResult <= 0) {
            TaskNode childNodeRefresh = taskNodeRepository.selectByRequestId(pendingNode.getRequestId());
            // 如果节点状态不是pending，可能是人工修改数据了，也可能是用户的执行策略实现有问题，例如node A有三个父节点B、C、D
            // 用户设置了节点B只要执行成功就返回A节点可执行，但是C执行PENDING的时候又返回了A节点应该PENDING，前后不一致；
            if (childNodeRefresh.getStatus() != TaskNodeStatus.PENDING) {
                // 监控
                flowMonitorService.nodeStatusAssertError(pendingNode.getRequestId(), TaskNodeStatus.PENDING,
                    childNodeRefresh.getStatus());
            }
        }

        // 因为任务直接被更新为pending了，所以我们只能在这里触发监控，其他地方触发不了了
        flowMonitorService.nodeFinish(pendingNode.getTaskRequestId(), pendingNode.getRequestId(),
            TaskNodeStatus.PENDING);
        // 唤醒该子节点，这个对子节点可能并发，不过不影响，唤醒是支持并发的，只会唤醒一次
        asyncTaskService.notifyTask(pendingNode.getRequestId());
    }
}
