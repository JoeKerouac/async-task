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
 * 执行状态
 * 
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public enum ExecStatus implements EnumInterface {

    INIT("INIT", "初始化状态，从未被执行，大多数任务会很快跳过该状态", "INIT"),

    WAIT("WAIT", "等待指定条件，条件满足后将会执行", "WAIT"),

    READY("READY", "就绪状态，任务已经可以执行了", "READY"),

    RUNNING("RUNNING", "执行中，任务正在执行中或者重试中", "RUNNING"),

    FINISH("FINISH", "执行完成", "FINISH"),

    ;

    static {
        // 重复检测
        EnumInterface.duplicateCheck(ExecStatus.class);
    }

    private final String code;
    private final String desc;
    private final String englishName;

    ExecStatus(String code, String desc, String englishName) {
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
