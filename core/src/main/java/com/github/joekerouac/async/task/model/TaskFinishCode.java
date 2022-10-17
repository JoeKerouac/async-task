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
 * 任务执行结束代码，解释任务为什么结束
 * 
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public enum TaskFinishCode implements EnumInterface {

    NONE("NONE", "当前任务还未执行完成", "NONE"),

    SUCCESS("SUCCESS", "执行成功", "SUCCESS"),

    RETRY_OVERFLOW("RETRY_OVERFLOW", "重试次数耗完仍然没有执行完毕", "RETRY_OVERFLOW"),

    NO_PROCESSOR("NO_PROCESSOR", "没有找到对应的执行器", "NO_PROCESSOR"),

    CANNOT_RETRY("CANNOT_RETRY", "没有找到对应的执行器", "CANNOT_RETRY"),

    USER_ERROR("USER_ERROR", "用户处理器返回的结果是ERROR", "USER_ERROR"),

    DESERIALIZATION_ERROR("DESERIALIZATION_ERROR", "任务反序列化异常", "DESERIALIZATION_ERROR"),;

    static {
        // 重复检测
        EnumInterface.duplicateCheck(TaskFinishCode.class);
    }

    private final String code;
    private final String desc;
    private final String englishName;

    TaskFinishCode(String code, String desc, String englishName) {
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
