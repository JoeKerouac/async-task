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
package com.github.joekerouac.async.task.flow.impl.repository;

import java.sql.ResultSet;
import java.util.List;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import com.github.joekerouac.async.task.Const;
import com.github.joekerouac.async.task.db.AbstractRepository;
import com.github.joekerouac.async.task.flow.model.TaskNodeMap;
import com.github.joekerouac.async.task.flow.spi.TaskNodeMapRepository;
import com.github.joekerouac.async.task.spi.AsyncTransactionManager;
import com.github.joekerouac.async.task.spi.TableNameSelector;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public class TaskNodeMapRepositoryImpl extends AbstractRepository implements TaskNodeMapRepository {

    public static final String DEFAULT_TABLE_NAME = "task_node_map";

    private static final String GET_ALL_PARENT = "select * from {} where `task_request_id` = ? and `child_node` = ?";

    private static final String GET_ALL_CHILD = "select * from {} where `task_request_id` = ? and `parent_node` = ?";

    private static final String GET_ALL_BY_TASK = "select * from {} where `task_request_id` = ? limit ? offset ?";

    public TaskNodeMapRepositoryImpl(@NotNull final AsyncTransactionManager transactionManager) {
        this(transactionManager, task -> DEFAULT_TABLE_NAME);
    }

    public TaskNodeMapRepositoryImpl(@NotNull final AsyncTransactionManager transactionManager,
        @NotNull final TableNameSelector tableNameSelector) {
        super(transactionManager, tableNameSelector, TaskNodeMap.class);
    }

    @Override
    public int save(final List<TaskNodeMap> nodeMaps) {
        return batchInsert(nodeMaps.get(0).getTaskRequestId(), nodeMaps);
    }

    @Override
    public List<String> getAllParent(final String taskRequestId, final String nodeRequestId) {
        return getAll(taskRequestId, nodeRequestId, GET_ALL_PARENT).stream().map(TaskNodeMap::getParentNode)
            .filter(req -> !Const.NULL.equals(req)).collect(Collectors.toList());
    }

    @Override
    public List<String> getAllChild(final String taskRequestId, final String nodeRequestId) {
        return getAll(taskRequestId, nodeRequestId, GET_ALL_CHILD).stream().map(TaskNodeMap::getChildNode)
            .filter(req -> !Const.NULL.equals(req)).collect(Collectors.toList());
    }

    @Override
    public List<TaskNodeMap> selectByTaskRequestId(String taskRequestId, int offset, int limit) {
        return runSql(taskRequestId, GET_ALL_BY_TASK, preparedStatement -> {
            ResultSet resultSet = preparedStatement.executeQuery();
            return buildModel(resultSet);
        }, taskRequestId, limit, offset);
    }

    private List<TaskNodeMap> getAll(final String taskId, final String nodeRequestId, String sql) {
        return runSql(taskId, sql, preparedStatement -> {
            ResultSet resultSet = preparedStatement.executeQuery();
            return buildModel(resultSet);
        }, taskId, nodeRequestId);
    }
}
