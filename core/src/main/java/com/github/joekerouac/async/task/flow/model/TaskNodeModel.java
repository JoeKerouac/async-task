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

import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import com.github.joekerouac.async.task.flow.enums.FailStrategy;
import com.github.joekerouac.async.task.flow.impl.StrategyConst;

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
public class TaskNodeModel {

    /**
     * 任务幂等请求ID，该ID需要全局唯一
     */
    @Size(max = 200)
    @NotBlank
    private String requestId;

    /**
     * 实际的节点任务数据；
     */
    @NotNull
    private Object data;

    /**
     * 对应的处理器，允许为null，为null时默认使用任务的类名（不带包名）
     */
    @Size(max = 100)
    private String processor;

    /**
     * 节点失败策略，如果可以接受节点失败，那么可以使用IGNORE策略
     */
    @NotNull
    private FailStrategy failStrategy = FailStrategy.PENDING;

    /**
     * 节点执行策略，默认必须父节点全部执行成功才能执行本节点；
     */
    @Size(max = 100)
    @NotBlank
    private String executeStrategy = StrategyConst.ALL_PARENT_SUCCESS_STRATEGY;

    /**
     * 最大重试次数
     */
    @Min(0)
    private int maxRetry = 6;

    /**
     * 节点执行策略上下文，允许为null
     */
    @Size(max = 1000)
    private String strategyContext;

    /**
     * 该节点的所有子节点，允许为null
     */
    @Valid
    private List<TaskNodeModel> allChild;

}
