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
package com.github.joekerouac.async.task.db;

import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import javax.validation.constraints.NotNull;

import com.github.joekerouac.async.task.exception.AsyncTaskExceptionProviderConst;
import com.github.joekerouac.async.task.exception.DBException;
import com.github.joekerouac.async.task.exception.SystemException;
import com.github.joekerouac.async.task.function.SqlRunner;
import com.github.joekerouac.async.task.spi.AsyncTransactionManager;
import com.github.joekerouac.async.task.spi.TableNameSelector;
import com.github.joekerouac.common.tools.constant.ExceptionProviderConst;
import com.github.joekerouac.common.tools.reflect.AccessorUtil;
import com.github.joekerouac.common.tools.reflect.ReflectUtil;
import com.github.joekerouac.common.tools.reflect.type.JavaTypeUtil;
import com.github.joekerouac.common.tools.string.StringUtils;
import com.github.joekerouac.common.tools.util.Assert;

import lombok.CustomLog;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
@CustomLog
public abstract class AbstractRepository {

    /**
     * 批量插入最大数量
     */
    private static final int BATCH_INSERT_SIZE = 100;

    /**
     * 事务管理器
     */
    private AsyncTransactionManager transactionManager;

    /**
     * 表名选择器，允许用户选择当前使用哪个表，这样用户就有机会来做分库分表
     */
    private TableNameSelector tableNameSelector;

    /**
     * 对应的模型类型
     */
    private Class<?> modelType;

    /**
     * 字段名与字段的映射
     */
    private LinkedHashMap<String, Field> fieldMap;

    private String insert;

    public AbstractRepository(@NotNull final AsyncTransactionManager transactionManager,
        @NotNull final TableNameSelector tableNameSelector, Class<?> modelType) {
        init(transactionManager, tableNameSelector, modelType);
    }

    protected void init(@NotNull final AsyncTransactionManager transactionManager,
        @NotNull final TableNameSelector tableNameSelector, Class<?> modelType) {
        this.transactionManager = transactionManager;
        this.tableNameSelector = tableNameSelector;
        this.modelType = modelType;
        this.fieldMap = new LinkedHashMap<>();

        StringBuilder insertBuilder = new StringBuilder();
        StringBuilder params = new StringBuilder();
        insertBuilder.append("insert into `{}` (");
        boolean first = true;

        for (final Field declaredField : ReflectUtil.getAllFields(modelType)) {
            // 跳过static和transient字段
            if (AccessorUtil.isStatic(declaredField) || AccessorUtil.isTransient(declaredField)) {
                continue;
            }

            declaredField.setAccessible(true);
            String columnName = declaredField.getName().replaceAll("[A-Z]", "_$0").toLowerCase();
            // 驼峰转下划线
            fieldMap.put(columnName, declaredField);
            if (first) {
                first = false;
            } else {
                insertBuilder.append(", ");
                params.append(", ");
            }
            insertBuilder.append("`").append(columnName).append("`");
            params.append("?");
        }

        insertBuilder.append(") values (").append(params).append(")");
        this.insert = insertBuilder.toString();
    }

    /**
     * 执行SQL
     *
     * @param requestId
     *            requestId
     * @param sqlTemplate
     *            sql模板，sql模板中表名以{}作为占位符
     * @param runner
     *            sql执行器
     * @param params
     *            PreparedStatement的参数，将会按照顺序设置到PreparedStatement中
     * @param <T>
     *            结果类型
     * @return 结果
     */
    protected <T> T runSql(String requestId, String sqlTemplate, SqlRunner<T> runner, Object... params) {
        String tableName = tableNameSelector.select(requestId);
        Assert.notBlank(tableName, StringUtils.format("当前表名获取为空，当前requestId: [{}]", requestId),
            ExceptionProviderConst.IllegalStateExceptionProvider);

        try {
            return transactionManager.run(requestId, connection -> {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("当前sqlTemplate：[{}], 当前requestId: [{}] ,当前表名为：[{}], 当前数据源为: [{}], 参数: [{}]",
                        sqlTemplate, requestId, tableName, connection, params);
                }
                PreparedStatement preparedStatement =
                    connection.prepareStatement(StringUtils.format(sqlTemplate, tableName));
                setParams(preparedStatement, params);
                return runner.run(preparedStatement);
            });
        } catch (SQLException sqlException) {
            throw new DBException(sqlException);
        }
    }

    /**
     * 从结果集中构建数据模型
     * 
     * @param resultSet
     *            结果集
     * @param <T>
     *            数据模型类型
     * @return 数据模型列表
     * @throws SQLException
     *             SQL异常
     */
    @SuppressWarnings("unchecked")
    protected <T> List<T> buildModel(ResultSet resultSet) throws SQLException {
        ResultSetMetaData metaData = resultSet.getMetaData();
        int columnCount = metaData.getColumnCount();
        List<T> list = new ArrayList<>();

        while (resultSet.next()) {
            T instance;
            try {
                instance = (T)modelType.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new SystemException(StringUtils.format("数据库模型 [{}] 创建实例失败，请检查该类型是否有公共无参构造器", modelType), e);
            }

            for (int i = 0; i < columnCount; i++) {
                String columnLabel = metaData.getColumnLabel(i + 1);
                Field field = fieldMap.get(columnLabel);
                Assert.notNull(field, StringUtils.format("数据库字段 [{}] 在类型 [{}] 中没有对应的映射", columnLabel, modelType),
                    AsyncTaskExceptionProviderConst.SystemException);

                Object result = getResult(resultSet, i + 1, field.getType());
                try {
                    field.set(instance, result);
                } catch (IllegalAccessException e) {
                    throw new SystemException(
                        StringUtils.format("访问类型 [{}] 的字段 [{}] 时出现权限异常，请检查当前系统的反射调用权限", modelType, field), e);
                }
            }
            list.add(instance);
        }

        return list;
    }

    /**
     * 批量插入（如果有分库分表，则批量插入的数据必须在同一分库分表）
     * 
     * @param requestId
     *            插入数据的requestId
     * @param models
     *            要插入的数据
     * @param <T>
     *            数据实际类型
     * @return 插入成功数量
     */
    protected <T> int batchInsert(String requestId, List<T> models) {
        return runSql(requestId, insert, preparedStatement -> {
            int batchCount = 0;
            int batchResult = 0;
            for (final T model : models) {
                int index = 1;
                for (final Field field : fieldMap.values()) {
                    Object fieldValue;
                    try {
                        fieldValue = field.get(model);
                    } catch (IllegalAccessException e) {
                        throw new SystemException(
                            StringUtils.format("访问类型 [{}] 的字段 [{}] 时出现权限异常，请检查当前系统的反射调用权限", modelType, field), e);
                    }

                    setParam(preparedStatement, index, fieldValue);

                    index++;
                }

                if (models.size() > 1) {
                    preparedStatement.addBatch();
                    batchCount += 1;
                    if (batchCount % BATCH_INSERT_SIZE == 0) {
                        // 批处理提交
                        batchResult += Arrays.stream(preparedStatement.executeBatch()).sum();
                        batchCount = 0;
                    }
                } else {
                    return preparedStatement.executeUpdate();
                }
            }

            if (batchCount > 0) {
                batchResult += Arrays.stream(preparedStatement.executeBatch()).sum();
            }

            return batchResult;
        });
    }

    /**
     * 设置参数集合
     * 
     * @param preparedStatement
     *            preparedStatement
     * @param params
     *            要设置的参数集合，参数集合将会按照传入顺序设置进preparedStatement
     * @throws SQLException
     *             SQL异常
     */
    protected void setParams(PreparedStatement preparedStatement, Object... params) throws SQLException {
        if (params == null || params.length == 0) {
            return;
        }

        int start = 1;
        for (final Object param : params) {
            setParam(preparedStatement, start++, param);
        }
    }

    /**
     * 设置参数
     * 
     * @param preparedStatement
     *            preparedStatement
     * @param columnIndex
     *            参数index
     * @param param
     *            参数
     * @throws SQLException
     *             SQL异常
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected void setParam(PreparedStatement preparedStatement, int columnIndex, Object param) throws SQLException {
        if (param == null) {
            preparedStatement.setObject(columnIndex, null);
        } else {
            TypeHandler typeHandler = getTypeHandler(param.getClass());
            typeHandler.setParameter(preparedStatement, columnIndex, param);
        }
    }

    /**
     * 获取结果
     * 
     * @param resultSet
     *            结果集
     * @param columnIndex
     *            结果index
     * @param javaType
     *            结果类型
     * @param <T>
     *            结果实际类型
     * @return 结果
     * @throws SQLException
     *             SQL异常
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected <T> T getResult(ResultSet resultSet, int columnIndex, Class<T> javaType) throws SQLException {
        TypeHandler typeHandler = getTypeHandler(javaType);
        return (T)typeHandler.getResult(resultSet, columnIndex, javaType);
    }

    /**
     * 获取类型处理器
     * 
     * @param type
     *            类型
     * @return 类型处理器
     */
    private TypeHandler<?> getTypeHandler(Class<?> type) {
        Class<?> usedType;
        if (JavaTypeUtil.isGeneralType(type)) {
            usedType = JavaTypeUtil.boxed(type);
        } else {
            usedType = type;
        }

        TypeHandler<?> typeHandler = TypeHandlerRegistry.get(usedType);
        Assert.notNull(typeHandler, StringUtils.format("当前系统类型 [{}] 没有对应的类型处理器", type),
            AsyncTaskExceptionProviderConst.SystemException);
        return typeHandler;
    }

}
