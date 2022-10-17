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

import java.util.Collections;
import java.util.List;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import com.github.joekerouac.async.task.flow.enums.TaskNodeStatus;
import com.github.joekerouac.async.task.flow.model.TaskNode;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public interface TaskNodeRepository {

    /**
     * 批量保存节点任务
     * 
     * @param nodes
     *            节点任务列表
     */
    void save(@NotEmpty List<TaskNode> nodes);

    /**
     * 根据requestId查询任务节点
     * 
     * @param nodeRequestId
     *            requestId
     * @return 任务节点
     */
    default TaskNode selectByRequestId(@NotBlank String nodeRequestId) {
        List<TaskNode> taskNodes = selectByRequestIds(Collections.singletonList(nodeRequestId));
        return taskNodes == null || taskNodes.isEmpty() ? null : taskNodes.get(0);
    }

    /**
     * 根据request id列表查询出对应的任务节点
     * 
     * @param nodeRequestIds
     *            requestId列表
     * @return requestId列表对应的任务节点列表
     */
    List<TaskNode> selectByRequestIds(@NotEmpty List<String> nodeRequestIds);

    /**
     * 根据主任务ID和子任务状态分页查询子任务，结果应该按照创建时间升序排序
     * 
     * @param taskRequestId
     *            主任务requestId
     * @param nodeStatus
     *            子任务状态
     * @param offset
     *            分页起始位置，从0开始
     * @param limit
     *            分页大小，最大100
     * @return 符合条件的子任务
     */
    List<TaskNode> selectByStatus(@NotBlank String taskRequestId, @NotNull TaskNodeStatus nodeStatus,
        @Min(0) int offset, @Min(1) @Max(200) int limit);

    /**
     * cas更新节点状态
     * 
     * @param nodeRequestId
     *            节点requestId
     * @param beforeStatus
     *            当前状态
     * @param updateStatus
     *            更新后的状态
     * @return 返回大于0表示更新成功（确切的说应该是返回1表示更新成功）
     */
    int casUpdateStatus(@NotBlank String nodeRequestId, @NotNull TaskNodeStatus beforeStatus,
        @NotNull TaskNodeStatus updateStatus);

    /**
     * 更新节点状态
     * 
     * @param nodeRequestId
     *            节点requestId
     * @param status
     *            要更新的状态
     * @return 返回大于0表示更新成功（确切的说应该是返回1表示更新成功）
     */
    int updateStatus(@NotBlank String nodeRequestId, @NotNull TaskNodeStatus status);

    /**
     * 根据传入的节点任务requestId集合批量更新这些任务的状态
     * 
     * @param nodeRequestIds
     *            节点任务requestId集合
     * @param status
     *            要更新的状态
     * @return 更新结果（影响行数）
     */
    int batchUpdateStatus(@NotEmpty List<String> nodeRequestIds, @NotNull TaskNodeStatus status);

}
