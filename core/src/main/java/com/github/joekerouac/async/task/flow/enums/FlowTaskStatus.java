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
package com.github.joekerouac.async.task.flow.enums;

import com.github.joekerouac.common.tools.enums.EnumInterface;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public enum FlowTaskStatus implements EnumInterface {

    INIT("INIT", "任务初始化", "INIT"),

    RUNNING("RUNNING", "任务执行中", "RUNNING"),

    PENDING("PENDING", "任务被挂起了", "PENDING"),

    FINISH("FINISH", "任务执行结束（可能是成功，也可能是失败）", "FINISH"),

    ;

    static {
        // 重复检测
        EnumInterface.duplicateCheck(FlowTaskStatus.class);
    }

    private final String code;
    private final String desc;
    private final String englishName;

    FlowTaskStatus(String code, String desc, String englishName) {
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
