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
 * 取消状态
 *
 * @author JoeKerouac
 * @date 2022-12-29 11:15
 * @since 2.0.0
 */
public enum CancelStatus implements EnumInterface {

    SUCCESS("SUCCESS", "取消成功", "SUCCESS"),

    RUNNING("RUNNING", "任务正在执行中，取消失败", "RUNNING"),

    FINISH("FINISH", "任务已经执行完成，取消失败", "FINISH"),

    NOT_EXIST("NOT_EXIST", "任务不存在 ，取消失败", "NOT_EXIST"),

    UNKNOWN("UNKNOWN", "取消失败，原因未知，可能是并发取消任务了", "UNKNOWN"),

    ;

    static {
        // 重复检测
        EnumInterface.duplicateCheck(ExecResult.class);
    }

    private final String code;
    private final String desc;
    private final String englishName;

    CancelStatus(String code, String desc, String englishName) {
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
