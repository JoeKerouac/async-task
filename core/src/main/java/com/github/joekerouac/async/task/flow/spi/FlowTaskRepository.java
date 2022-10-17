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

import java.util.List;

import com.github.joekerouac.async.task.flow.enums.FlowTaskStatus;
import com.github.joekerouac.async.task.flow.enums.FlowTaskType;
import com.github.joekerouac.async.task.flow.model.FlowTask;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public interface FlowTaskRepository {

    /**
     * 保存任务
     * 
     * @param flowTask
     *            任务
     */
    void save(FlowTask flowTask);

    /**
     * 根据requestId查询主任务
     *
     * @param requestId
     *            主任务requestId
     * @return 主任务
     */
    FlowTask select(String requestId);

    /**
     * 根据requestId锁定主任务
     * 
     * @param requestId
     *            主任务requestId
     * @return 锁定的主任务
     */
    FlowTask selectForLock(String requestId);

    /**
     * 更新主任务状态
     * 
     * @param requestId
     *            主任务的requestId
     * @param status
     *            主任务状态
     * @return 影响行数
     */
    int updateStatus(String requestId, FlowTaskStatus status);

    /**
     * 更新最后一个
     * 
     * @param requestId
     *            主任务的requestId
     * @param lastTaskRequestId
     *            子任务的requestId
     * @return 影响行数
     */
    int updateLastTaskId(String requestId, String lastTaskRequestId);

    /**
     * 根据任务类型分页查询对应的任务，结果应该按照创建时间升序排序
     * 
     * @param type
     *            任务类型
     * @param offset
     *            起始位置，从0开始
     * @param limit
     *            分页大小，最大100
     * @return 符合条件的主任务
     */
    List<FlowTask> selectByType(FlowTaskType type, int offset, int limit);

}
