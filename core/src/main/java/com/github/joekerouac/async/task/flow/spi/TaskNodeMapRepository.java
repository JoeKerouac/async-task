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

import com.github.joekerouac.async.task.flow.model.TaskNodeMap;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public interface TaskNodeMapRepository {

    /**
     * 批量保存节点关系
     * 
     * @param nodeMaps
     *            带保存的节点关系
     * @return 保存成功数量
     */
    int save(List<TaskNodeMap> nodeMaps);

    /**
     * 获取指定任务的所有父节点
     * 
     * @param taskRequestId
     *            主任务 request ID
     * @param nodeRequestId
     *            节点 request ID
     * @return 指定任务节点的所有父节点ID
     */
    List<String> getAllParent(String taskRequestId, String nodeRequestId);

    /**
     * 获取指定任务的所有子节点
     * 
     * @param taskRequestId
     *            主任务 request ID
     * @param nodeRequestId
     *            节点 request ID
     * @return 指定任务节点的所有子节点ID
     */
    List<String> getAllChild(String taskRequestId, String nodeRequestId);

    /**
     * 根据taskRequestId分页查询TaskNodeMap
     * 
     * @param taskRequestId
     *            taskRequestId
     * @param offset
     *            offset
     * @param limit
     *            limit
     * @return 结果
     */
    List<TaskNodeMap> selectByTaskRequestId(String taskRequestId, int offset, int limit);

}
