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
import java.util.Collections;
import java.util.List;

import javax.validation.constraints.NotNull;

import com.github.joekerouac.async.task.db.AbstractRepository;
import com.github.joekerouac.async.task.db.DBFuture;
import com.github.joekerouac.async.task.flow.enums.FlowTaskStatus;
import com.github.joekerouac.async.task.flow.enums.FlowTaskType;
import com.github.joekerouac.async.task.flow.model.FlowTask;
import com.github.joekerouac.async.task.flow.spi.FlowTaskRepository;
import com.github.joekerouac.async.task.spi.AsyncTransactionManager;
import com.github.joekerouac.async.task.spi.TableNameSelector;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public class FlowTaskRepositoryImpl extends AbstractRepository implements FlowTaskRepository {

    public static final String DEFAULT_TABLE_NAME = "flow_task";

    private static final String SELECT = "select * from {} where `request_id` = ?";

    private static final String SELECT_FOR_LOCK = "select * from {} where `request_id` = ? for update";

    private static final String UPDATE_FOR_LOCK = "update {} set `request_id` = `request_id` where `request_id` = ?";

    private static final String SELECT_BY_TYPE =
        "select * from {} where `type` = ? and `status` = 'RUNNING' " + "limit ? offset ?";

    private static final String UPDATE_STATUS =
        "update {} set `status` = ?, `gmt_update_time` = ? where `request_id` = ?";

    private static final String UPDATE_LAST_TASK_ID =
        "update {} set `last_task_id` = ?, `gmt_update_time` = ? where `request_id` = ?";

    public FlowTaskRepositoryImpl(@NotNull final AsyncTransactionManager transactionManager) {
        this(transactionManager, task -> DEFAULT_TABLE_NAME);
    }

    public FlowTaskRepositoryImpl(@NotNull final AsyncTransactionManager transactionManager,
        @NotNull final TableNameSelector tableNameSelector) {
        super(transactionManager, tableNameSelector, FlowTask.class);
    }

    @Override
    public void save(final FlowTask flowTask) {
        batchInsert(flowTask.getRequestId(), Collections.singletonList(flowTask));
    }

    @Override
    public FlowTask select(final String requestId) {
        return internalSelect(requestId, false);
    }

    @Override
    public FlowTask selectForLock(final String requestId) {
        return internalSelect(requestId, true);
    }

    @Override
    public int updateStatus(final String requestId, final FlowTaskStatus status) {
        return runSql(requestId, UPDATE_STATUS, PreparedStatement::executeUpdate, status, LocalDateTime.now(),
            requestId);
    }

    @Override
    public int updateLastTaskId(final String requestId, final String lastTaskRequestId) {
        return runSql(requestId, UPDATE_LAST_TASK_ID, PreparedStatement::executeUpdate, lastTaskRequestId,
            LocalDateTime.now(), requestId);
    }

    @Override
    public List<FlowTask> selectByType(final FlowTaskType type, final int offset, final int limit) {
        return runSql(null, SELECT_BY_TYPE, preparedStatement -> {
            ResultSet resultSet = preparedStatement.executeQuery();
            return buildModel(resultSet);
        }, type, limit, offset);
    }

    private FlowTask internalSelect(final String requestId, final boolean lock) {
        boolean needLock = lock;
        if (needLock && !DBFuture.getSupportSelectForUpdate()) {
            // 需要加锁，并且当前线程配置的不支持select for update，这里尝试先update来锁定数据
            runSql(requestId, UPDATE_FOR_LOCK, PreparedStatement::executeUpdate, requestId);
            needLock = false;
        }

        return runSql(requestId, needLock ? SELECT_FOR_LOCK : SELECT, preparedStatement -> {
            ResultSet resultSet = preparedStatement.executeQuery();
            List<FlowTask> result = buildModel(resultSet);
            return result.isEmpty() ? null : result.get(0);
        }, requestId);
    }

}
