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
package com.github.joekerouac.async.task.impl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.sql.DataSource;
import javax.validation.constraints.NotNull;

import com.github.joekerouac.async.task.db.AbstractRepository;
import com.github.joekerouac.async.task.entity.AsyncTask;
import com.github.joekerouac.async.task.model.ExecStatus;
import com.github.joekerouac.async.task.model.TaskFinishCode;
import com.github.joekerouac.async.task.spi.AsyncTaskRepository;
import com.github.joekerouac.async.task.spi.ConnectionSelector;
import com.github.joekerouac.async.task.spi.TableNameSelector;
import com.github.joekerouac.common.tools.collection.CollectionUtil;
import com.github.joekerouac.common.tools.constant.StringConst;
import com.github.joekerouac.common.tools.db.SqlUtil;
import com.github.joekerouac.common.tools.exception.ExceptionUtil;
import com.github.joekerouac.common.tools.string.StringUtils;

import lombok.CustomLog;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
@CustomLog
public class AsyncTaskRepositoryImpl extends AbstractRepository implements AsyncTaskRepository {

    private static final String DEFAULT_TABLE_NAME = "async_task";

    private static final String PLACEHOLDER = "{placeholder}";

    private static final String SQL_SELECT_BY_ID = "select * from {} where `request_id` = ?";

    private static final String SQL_CAS_UPDATE =
        "update {} set `status` = ?, `exec_ip` = ?, `gmt_update_time` = ? where `request_id` = ? and `status` = ?";

    private static final String SQL_CAS_CANCEL =
        "update {} set `status` = \"" + ExecStatus.FINISH + "\", `task_finish_code` = \"" + TaskFinishCode.CANCEL.code()
            + "\", `exec_ip` = ?, `gmt_update_time` = ? where `request_id` = ? and `status` = ?";

    private static final String SQL_UPDATE = "update {} set {setTemp}, `gmt_update_time` = ? where `request_id` = ?";

    private static final String SQL_SELECT_PAGE =
        "select * from {} where `status` = ? and `exec_time` <= ? {exclude} order by `exec_time` desc limit ? offset ?";

    private static final String SQL_SELECT_FINISH_PAGE =
        "select * from {} where `processor` = ? and `task_finish_code` = ? and `status` = 'FINISH' and `exec_time` <= ? "
            + "order by `exec_time` asc limit ? offset ?";

    private static final String SQL_DELETE = "delete from {} where `request_id` in (" + PLACEHOLDER + ")";

    /**
     * 统计sql
     */
    private static final String SQL_STAT =
        "select * from {} where `status` = '" + ExecStatus.RUNNING.code() + "' and `gmt_update_time` <= ?";

    public AsyncTaskRepositoryImpl(DataSource dataSource) {
        this(dataSource, DEFAULT_TABLE_NAME);
    }

    public AsyncTaskRepositoryImpl(DataSource dataSource, String tableName) {
        super(dataSource, tableName, AsyncTask.class);
    }

    public AsyncTaskRepositoryImpl(@NotNull final ConnectionSelector connectionSelector) {
        this(connectionSelector, task -> DEFAULT_TABLE_NAME);
    }

    public AsyncTaskRepositoryImpl(@NotNull final ConnectionSelector connectionSelector,
        @NotNull final TableNameSelector tableNameSelector) {
        super(connectionSelector, tableNameSelector, AsyncTask.class);
    }

    @Override
    public boolean save(final AsyncTask task) {
        try {
            batchInsert(task.getRequestId(), Collections.singletonList(task));
            return true;
        } catch (RuntimeException e) {
            final Throwable rootCause = ExceptionUtil.getRootCause(e);
            if (rootCause instanceof SQLException) {
                if (SqlUtil.causeDuplicateKey((SQLException)rootCause)) {
                    return false;
                }
            }
            throw e;
        }
    }

    @Override
    public AsyncTask selectByRequestId(final String requestId) {
        return runSql(requestId, SQL_SELECT_BY_ID, preparedStatement -> {
            ResultSet resultSet = preparedStatement.executeQuery();
            List<AsyncTask> list = buildModel(resultSet);
            return list.isEmpty() ? null : list.get(0);
        }, requestId);
    }

    @Override
    public int casUpdate(final String requestId, final ExecStatus before, final ExecStatus after, final String ip) {
        return runSql(requestId, SQL_CAS_UPDATE, PreparedStatement::executeUpdate, after, ip, LocalDateTime.now(),
            requestId, before);
    }

    @Override
    public int casCancel(String requestId, ExecStatus before, String ip) {
        return runSql(requestId, SQL_CAS_CANCEL, PreparedStatement::executeUpdate, ip, LocalDateTime.now(), requestId,
            before);
    }

    @Override
    public int update(final String requestId, final ExecStatus status, final TaskFinishCode code,
        final LocalDateTime execTime, final Integer retry, final String ip) {
        StringBuilder sb = new StringBuilder();

        if (status != null) {
            sb.append(", `status` = ?");
        }

        if (code != null) {
            sb.append(", `task_finish_code` = ?");
        }

        if (execTime != null) {
            sb.append(", `exec_time` = ?");
        }

        if (retry != null) {
            sb.append(", `retry` = ?");
        }

        if (ip != null) {
            sb.append(", `exec_ip` = ?");
        }

        String setTemp = sb.substring(1);

        return runSql(requestId, SQL_UPDATE.replace("{setTemp}", setTemp), preparedStatement -> {
            int start = 1;

            if (status != null) {
                setParam(preparedStatement, start++, status);
            }

            if (code != null) {
                setParam(preparedStatement, start++, code);
            }

            if (execTime != null) {
                setParam(preparedStatement, start++, execTime);
            }

            if (retry != null) {
                setParam(preparedStatement, start++, retry);
            }

            if (ip != null) {
                setParam(preparedStatement, start++, ip);
            }

            setParam(preparedStatement, start++, LocalDateTime.now());
            setParam(preparedStatement, start, requestId);
            return preparedStatement.executeUpdate();
        });
    }

    @Override
    public List<AsyncTask> selectPage(final ExecStatus status, final LocalDateTime dateTime,
        final Collection<String> skipTaskRequestIds, final int offset, final int limit) {
        String exclude;
        if (skipTaskRequestIds.isEmpty()) {
            exclude = StringConst.EMPTY;
        } else {
            String excludeTemp = "and `request_id` not in ({idList})";
            exclude = excludeTemp.replace("{idList}", StringUtils.copy(", ?", skipTaskRequestIds.size()).substring(1));
        }

        Object[] params = new Object[4 + skipTaskRequestIds.size()];
        int start = 0;
        params[start++] = status;
        params[start++] = dateTime;

        for (final String requestId : skipTaskRequestIds) {
            params[start++] = requestId;
        }

        params[start++] = limit;
        params[start] = offset;

        return runSql(null, SQL_SELECT_PAGE.replace("{exclude}", exclude), preparedStatement -> {
            ResultSet resultSet = preparedStatement.executeQuery();
            return buildModel(resultSet);
        }, params);
    }

    @Override
    public List<AsyncTask> selectFinishPage(final String processor, final TaskFinishCode finishCode,
        final LocalDateTime dateTime, final int offset, final int limit) {
        Object[] params = new Object[5];
        int start = 0;
        params[start++] = processor;
        params[start++] = finishCode;
        params[start++] = dateTime;
        params[start++] = limit;
        params[start] = offset;

        return runSql(null, SQL_SELECT_FINISH_PAGE, preparedStatement -> {
            ResultSet resultSet = preparedStatement.executeQuery();
            return buildModel(resultSet);
        }, params);

    }

    @Override
    public int delete(final List<String> requestIds) {
        if (CollectionUtil.isEmpty(requestIds)) {
            return 0;
        }

        String paramsPlaceholder = StringUtils.copy(", ?", requestIds.size());

        return runSql(requestIds.get(0), SQL_DELETE.replace(PLACEHOLDER, paramsPlaceholder.substring(1)),
            PreparedStatement::executeUpdate, requestIds.toArray());
    }

    @Override
    public List<AsyncTask> stat(LocalDateTime execTime) {
        return runSql(null, SQL_STAT, preparedStatement -> {
            ResultSet resultSet = preparedStatement.executeQuery();
            return buildModel(resultSet);
        }, new Object[] {execTime});
    }
}
