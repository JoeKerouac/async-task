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
package com.github.joekerouac.async.task.flow.model;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import com.github.joekerouac.async.task.entity.common.DatabaseObj;
import com.github.joekerouac.async.task.flow.enums.FailStrategy;
import com.github.joekerouac.async.task.flow.enums.TaskNodeStatus;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 异步任务中的节点任务数据
 *
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
@Getter
@Setter
@ToString
public class TaskNode extends DatabaseObj {

    /**
     * 任务幂等请求ID；
     * 
     * 注意：
     * <li>这个ID一定要与生成的异步任务的requestId一致，否则无法唤醒</li>
     * <li>这个ID与主任务的分库分表ID一样要一致（如果分库分表的话）</li>
     */
    @Size(max = 200)
    @NotBlank
    private String requestId;

    /**
     * flow task的requestId，即本节点对应的主任务的ID
     */
    @Size(max = 200)
    @NotBlank
    private String taskRequestId;

    /**
     * 实际的节点任务数据
     */
    @Size(max = 2000)
    @NotBlank
    private String nodeData;

    /**
     * 对应的处理器
     */
    @Size(max = 100)
    @NotBlank
    private String processor;

    /**
     * 节点任务状态
     */
    @NotNull
    private TaskNodeStatus status;

    /**
     * 节点失败策略
     */
    @NotNull
    private FailStrategy failStrategy;

    /**
     * 节点执行策略
     */
    @Size(max = 100)
    @NotBlank
    private String executeStrategy;

    /**
     * 节点执行策略上下文，允许为null
     */
    @Size(max = 1000)
    private String strategyContext;

    /**
     * 最大重试次数
     */
    @Min(0)
    private int maxRetry;

}
