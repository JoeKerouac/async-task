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
package com.github.joekerouac.async.task.entity;

import java.time.LocalDateTime;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import com.github.joekerouac.async.task.entity.common.DatabaseObj;
import com.github.joekerouac.async.task.model.ExecStatus;
import com.github.joekerouac.async.task.model.TaskFinishCode;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 异步任务，注意，该任务的equals方法和hashcode方法已经重写，只要ID一致就认为是一致的；
 * 
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
@Getter
@Setter
@ToString(callSuper = true)
public class AsyncTask extends DatabaseObj {

    /**
     * 幂等ID
     */
    @NotBlank
    @Size(max = 200)
    private String requestId;

    /**
     * 任务详情
     */
    @NotBlank
    @Size(max = 2000)
    private String task;

    /**
     * 最大可重试次数
     */
    @Min(-1)
    private int maxRetry;

    /**
     * 任务开始执行时间，重试时会更新
     */
    @NotNull
    private LocalDateTime execTime;

    /**
     * 任务执行器
     */
    @NotBlank
    @Size(max = 100)
    private String processor;

    /**
     * 当前重试次数
     */
    @Min(0)
    private int retry;

    /**
     * 任务状态
     */
    @NotNull
    private ExecStatus status;

    /**
     * 任务执行结果码
     */
    @NotNull
    private TaskFinishCode taskFinishCode;

    /**
     * 创建任务的IP
     */
    @NotBlank
    private String createIp;

    /**
     * 执行任务的IP，在初始化阶段没有意义，只有在任务执行中或者执行完毕时才有意义
     */
    @NotBlank
    private String execIp;

}
