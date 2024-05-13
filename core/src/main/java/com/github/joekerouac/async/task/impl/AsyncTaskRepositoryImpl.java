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
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.validation.constraints.NotNull;

import com.github.joekerouac.async.task.db.AbstractRepository;
import com.github.joekerouac.async.task.entity.AsyncTask;
import com.github.joekerouac.async.task.model.ExecStatus;
import com.github.joekerouac.async.task.model.TaskFinishCode;
import com.github.joekerouac.async.task.spi.AsyncTaskRepository;
import com.github.joekerouac.async.task.spi.AsyncTransactionManager;
import com.github.joekerouac.async.task.spi.TableNameSelector;
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

    private static final String SQL_SELECT_FOR_UPDATE_BY_ID = "select * from {} where `request_id` = ? for update";

    private static final String SQL_CAS_UPDATE =
        "update {} set `status` = ?, `exec_ip` = ?, `gmt_update_time` = ? where `request_id` = ? and `status` = ? and `exec_ip` = ?";

    private static final String SQL_CAS_CANCEL =
        "update {} set `status` = \"" + ExecStatus.FINISH + "\", `task_finish_code` = \"" + TaskFinishCode.CANCEL.code()
            + "\", `exec_ip` = ?, `gmt_update_time` = ? where `request_id` = ? and `status` = ?";

    private static final String SQL_UPDATE = "update {} set {setTemp}, `gmt_update_time` = ? where `request_id` = ?";

    private static final String SQL_SELECT_PAGE =
        "select * from {} where `status` = ? and `exec_time` <= ? {dynamic} order by `exec_time` asc limit ? offset ?";

    private static final String SQL_SELECT_FINISH_PAGE =
        "select * from {} where `processor` = ? and `task_finish_code` in ('SUCCESS', 'CANCEL') and `status` = 'FINISH' and `exec_time` <= ?"
            + " limit ? offset ?";

    private static final String SQL_DELETE = "delete from {} where `request_id` in (" + PLACEHOLDER + ")";

    /**
     * 统计sql
     */
    private static final String SQL_STAT =
        "select * from {} where `status` = '" + ExecStatus.RUNNING.code() + "' and `gmt_update_time` <= ?";

    public AsyncTaskRepositoryImpl(@NotNull final AsyncTransactionManager transactionManager) {
        this(transactionManager, task -> DEFAULT_TABLE_NAME);
    }

    public AsyncTaskRepositoryImpl(@NotNull final AsyncTransactionManager transactionManager,
        @NotNull final TableNameSelector tableNameSelector) {
        super(transactionManager, tableNameSelector == null ? task -> DEFAULT_TABLE_NAME : tableNameSelector,
            AsyncTask.class);
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
    public AsyncTask selectForUpdate(String requestId) {
        return runSql(requestId, SQL_SELECT_FOR_UPDATE_BY_ID, preparedStatement -> {
            ResultSet resultSet = preparedStatement.executeQuery();
            List<AsyncTask> list = buildModel(resultSet);
            return list.isEmpty() ? null : list.get(0);
        }, requestId);
    }

    @Override
    public int casUpdate(final String requestId, final ExecStatus before, final ExecStatus after, String beforeIp,
        final String afterIp) {
        return runSql(requestId, SQL_CAS_UPDATE, PreparedStatement::executeUpdate, after, afterIp, LocalDateTime.now(),
            requestId, before, beforeIp);
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
    public List<AsyncTask> selectFinishPage(final String processor, final LocalDateTime dateTime, final int offset,
        final int limit) {
        Object[] params = new Object[4];
        int start = 0;
        params[start++] = processor;
        params[start++] = dateTime;
        params[start++] = limit;
        params[start] = offset;

        return runSql(null, SQL_SELECT_FINISH_PAGE, preparedStatement -> {
            ResultSet resultSet = preparedStatement.executeQuery();
            return buildModel(resultSet);
        }, params);

    }

    @Override
    public int delete(final Set<String> requestIds) {
        if (requestIds == null || requestIds.isEmpty()) {
            return 0;
        }

        String requestId = requestIds.iterator().next();
        String paramsPlaceholder = generatePlaceholder(requestIds.size());

        return runSql(requestId, SQL_DELETE.replace(PLACEHOLDER, paramsPlaceholder.substring(1)),
            PreparedStatement::executeUpdate, requestIds.toArray());
    }

    @Override
    public List<AsyncTask> stat(LocalDateTime execTime) {
        return runSql(null, SQL_STAT, preparedStatement -> {
            ResultSet resultSet = preparedStatement.executeQuery();
            return buildModel(resultSet);
        }, new Object[] {execTime});
    }

    @Override
    public List<AsyncTask> selectPage(ExecStatus status, LocalDateTime dateTime, int offset, int limit,
        Set<String> processorGroup, boolean contain) {
        String dynamic = StringConst.EMPTY;

        Object[] params = new Object[4 + processorGroup.size()];
        int start = 0;
        params[start++] = status;
        params[start++] = dateTime;

        if (!processorGroup.isEmpty()) {
            String dynamicProcessor = " and `processor` " + (contain ? " in " : " not in ") + " ({processorList}) ";
            dynamic += dynamicProcessor.replace("{processorList}", generatePlaceholder(processorGroup.size()));

            for (final String processor : processorGroup) {
                params[start++] = processor;
            }
        }

        params[start++] = limit;
        params[start] = offset;

        if (contain && processorGroup.isEmpty()) {
            return Collections.emptyList();
        } else {
            return runSql(null, SQL_SELECT_PAGE.replace("{dynamic}", dynamic), preparedStatement -> {
                ResultSet resultSet = preparedStatement.executeQuery();
                return buildModel(resultSet);
            }, params);
        }
    }

    /**
     * 生成占位符
     * 
     * @param count
     *            占位符数量
     * @return 占位符，例如?, ?, ?
     */
    private String generatePlaceholder(int count) {
        return StringUtils.copy(", ?", count).substring(1);
    }
}
