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
package com.github.joekerouac.async.task.model;

import com.github.joekerouac.common.tools.enums.EnumInterface;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public enum TransStrategy implements EnumInterface {

    REQUIRED("REQUIRED", "如果当前没有事务，则开启事务，如果当前存在事务，则加入事务", "REQUIRED"),

    SUPPORTS("SUPPORTS", "如果当前没有事务，则不使用事务，如果当前存在事务，则加入事务", "SUPPORTS"),

    MANDATORY("MANDATORY", "如果当前没有事务，则抛出异常，如果当前存在事务，则加入事务", "MANDATORY"),

    REQUIRES_NEW("REQUIRES_NEW", "无论当前是否有事务，都新开事务", "REQUIRES_NEW"),

    NOT_SUPPORTED("NOT_SUPPORTED", "以非事务的方式执行，如果当前存在事务，则暂停事务", "NOT_SUPPORTED"),

    NEVER("NEVER", "以非事务的方式执行，如果当前存在事务则抛出异常", "NEVER"),

    ;

    public static final TransStrategy DEFAULT = SUPPORTS;

    static {
        // 重复检测
        EnumInterface.duplicateCheck(TransStrategy.class);
    }

    private final String code;
    private final String desc;
    private final String englishName;

    TransStrategy(String code, String desc, String englishName) {
        this.code = code;
        this.desc = desc;
        this.englishName = englishName;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String desc() {
        return desc;
    }

    @Override
    public String englishName() {
        return englishName;
    }

}
