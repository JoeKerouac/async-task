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

import com.github.joekerouac.async.task.db.TypeHandler;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public class IntTypeHandler implements TypeHandler<Integer> {

    @Override
    public void setParameter(final PreparedStatement ps, final int i, final Integer parameter) throws SQLException {
        ps.setInt(i, parameter);
    }

    @Override
    public Integer getResult(final ResultSet rs, final int columnIndex, final Class<?> javaType) throws SQLException {
        return rs.getInt(columnIndex);
    }
}
