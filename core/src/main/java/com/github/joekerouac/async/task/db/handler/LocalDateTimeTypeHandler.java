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
package com.github.joekerouac.async.task.db.handler;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

import com.github.joekerouac.async.task.db.TypeHandler;
import com.github.joekerouac.common.tools.date.DateUtil;
import com.github.joekerouac.common.tools.string.StringUtils;

/**
 * 日期类型处理器 <br/>
 * <br/>
 * MySQL默认驱动，当我们直接将{@link java.time.LocalDateTime}作为参数设置进去时，最终底层是调用到了
 * {@link com.mysql.cj.ClientPreparedQueryBindings#setTimestamp(int, java.sql.Timestamp, Calendar, int)}这里，内部还是将其格式化为了
 * 字符串存储，但是这里使用了数据库的默认时区，如果数据库时区设置错误的话最终存储到数据库的时间就会出错（另外某些MySQL驱动有bug，时区写死了CST）； <br/>
 * <br/>
 * 而当我们从{@link ResultSet}里边直接取{@link java.sql.Timestamp}的时候，底层实际是调用了
 * {@link com.mysql.cj.result.SqlTimestampValueFactory#createFromTimestamp(int, int, int, int, int, int, int)}将年月日
 * 时分秒毫秒数据转为了时间戳，同时这里边使用的时区也是数据库默认时区；
 * 
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public class LocalDateTimeTypeHandler implements TypeHandler<LocalDateTime> {

    private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder().appendPattern(DateUtil.BASE)
        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 6, true).toFormatter();

    @Override
    public void setParameter(final PreparedStatement ps, final int i, final LocalDateTime parameter)
        throws SQLException {
        ps.setString(i, parameter.format(FORMATTER));
    }

    @Override
    public LocalDateTime getResult(final ResultSet rs, final int columnIndex, final Class<?> javaType)
        throws SQLException {
        String str = rs.getString(columnIndex);
        if (StringUtils.isBlank(str)) {
            return null;
        }
        return FORMATTER.parse(str, LocalDateTime::from);
    }
}
