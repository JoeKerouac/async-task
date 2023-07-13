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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;

import javax.sql.DataSource;
import javax.validation.constraints.NotNull;

import com.github.joekerouac.async.task.db.AbstractRepository;
import com.github.joekerouac.async.task.flow.enums.TaskNodeStatus;
import com.github.joekerouac.async.task.flow.model.TaskNode;
import com.github.joekerouac.async.task.flow.spi.TaskNodeRepository;
import com.github.joekerouac.async.task.spi.ConnectionSelector;
import com.github.joekerouac.async.task.spi.TableNameSelector;
import com.github.joekerouac.common.tools.string.StringUtils;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public class TaskNodeRepositoryImpl extends AbstractRepository implements TaskNodeRepository {

    public static final String DEFAULT_TABLE_NAME = "task_node";

    private static final String PLACEHOLDER = "{placeholder}";

    private static final String SELECT_BY_REQUEST_IDS =
        "select * from `{}` where `request_id` in (" + PLACEHOLDER + ")";

    private static final String SELECT_BY_STATUS =
        "select * from `{}` where `task_request_id` = ? and `status` = ? limit ? offset ?";

    private static final String CAS_UPDATE_STATUS =
        "update `{}` set `status` = ?, `gmt_update_time` = ? where `request_id` = ? and `status` = ?";

    private static final String UPDATE_STATUS =
        "update `{}` set `status` = ?, `gmt_update_time` = ? where `request_id` = ?";

    private static final String BATCH_UPDATE_STATUS =
        "update `{}` set `status` = ?, `gmt_update_time` = ? where `request_id` in (" + PLACEHOLDER + ")";

    public TaskNodeRepositoryImpl(DataSource dataSource) {
        this(dataSource, DEFAULT_TABLE_NAME);
    }

    public TaskNodeRepositoryImpl(DataSource dataSource, String tableName) {
        super(dataSource, tableName, TaskNode.class);
    }

    public TaskNodeRepositoryImpl(@NotNull final ConnectionSelector connectionSelector) {
        this(connectionSelector, task -> DEFAULT_TABLE_NAME);
    }

    public TaskNodeRepositoryImpl(@NotNull final ConnectionSelector connectionSelector,
        @NotNull final TableNameSelector tableNameSelector) {
        super(connectionSelector, tableNameSelector, TaskNode.class);
    }

    @Override
    public void save(final List<TaskNode> nodes) {
        batchInsert(nodes.get(0).getRequestId(), nodes);
    }

    @Override
    public List<TaskNode> selectByRequestIds(final List<String> nodeRequestIds) {
        String paramsPlaceholder = StringUtils.copy(", ?", nodeRequestIds.size());

        return runSql(nodeRequestIds.get(0), SELECT_BY_REQUEST_IDS.replace(PLACEHOLDER, paramsPlaceholder.substring(1)),
            preparedStatement -> {
                ResultSet resultSet = preparedStatement.executeQuery();
                return buildModel(resultSet);
            }, nodeRequestIds.toArray());
    }

    @Override
    public List<TaskNode> selectByStatus(final String taskRequestId, final TaskNodeStatus nodeStatus, final int offset,
        final int limit) {
        return runSql(taskRequestId, SELECT_BY_STATUS, preparedStatement -> {
            ResultSet resultSet = preparedStatement.executeQuery();
            return buildModel(resultSet);
        }, taskRequestId, nodeStatus, limit, offset);
    }

    @Override
    public int casUpdateStatus(final String nodeRequestId, final TaskNodeStatus beforeStatus,
        final TaskNodeStatus updateStatus) {
        return runSql(nodeRequestId, CAS_UPDATE_STATUS, PreparedStatement::executeUpdate, updateStatus,
            LocalDateTime.now(), nodeRequestId, beforeStatus);
    }

    @Override
    public int updateStatus(final String nodeRequestId, final TaskNodeStatus status) {
        return runSql(nodeRequestId, UPDATE_STATUS, PreparedStatement::executeUpdate, status, LocalDateTime.now(),
            nodeRequestId);
    }

    @Override
    public int batchUpdateStatus(final List<String> nodeRequestIds, final TaskNodeStatus status) {
        if (nodeRequestIds.isEmpty()) {
            return 0;
        }

        String paramsPlaceholder = StringUtils.copy(", ?", nodeRequestIds.size());

        Object[] params = new Object[2 + nodeRequestIds.size()];
        params[0] = status;
        params[1] = LocalDateTime.now();
        int start = 2;
        for (final String requestId : nodeRequestIds) {
            params[start++] = requestId;
        }

        return runSql(nodeRequestIds.get(0), BATCH_UPDATE_STATUS.replace(PLACEHOLDER, paramsPlaceholder.substring(1)),
            PreparedStatement::executeUpdate, params);
    }

}
