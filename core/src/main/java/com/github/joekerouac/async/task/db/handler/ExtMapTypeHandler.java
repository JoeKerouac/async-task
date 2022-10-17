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

import com.github.joekerouac.common.tools.reflect.type.AbstractTypeReference;
import com.github.joekerouac.common.tools.util.JsonUtil;
import com.github.joekerouac.async.task.Const;
import com.github.joekerouac.async.task.db.TypeHandler;
import com.github.joekerouac.async.task.entity.common.ExtMap;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public class ExtMapTypeHandler implements TypeHandler<ExtMap> {

    @Override
    public void setParameter(final PreparedStatement ps, final int i, final ExtMap parameter) throws SQLException {
        ps.setString(i, new String(JsonUtil.write(parameter), Const.DEFAULT_CHARSET));
    }

    @Override
    public ExtMap getResult(final ResultSet rs, final int columnIndex, final Class<?> javaType) throws SQLException {
        return JsonUtil.read(rs.getString(columnIndex).getBytes(Const.DEFAULT_CHARSET),
            new AbstractTypeReference<ExtMap>() {});
    }

}
