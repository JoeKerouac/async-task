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
package com.github.joekerouac.async.task.entity.common;

import java.time.LocalDateTime;
import java.util.Objects;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
@Getter
@Setter
@ToString
public class DatabaseObj {

    /**
     * 表ID
     */
    @Size(max = 100)
    @NotBlank
    protected String id;

    /**
     * 创建时间
     */
    @NotNull
    protected LocalDateTime gmtCreateTime = LocalDateTime.now();

    /**
     * 更新时间
     */
    @NotNull
    protected LocalDateTime gmtUpdateTime = LocalDateTime.now();

    /**
     * 扩展参数，存储时使用json格式字符串 varchar(2000)
     */
    @NotNull
    protected ExtMap<String, Object> extMap = new ExtMap<>();

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DatabaseObj that = (DatabaseObj)o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
