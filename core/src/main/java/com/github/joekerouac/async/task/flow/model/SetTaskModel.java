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

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import com.github.joekerouac.async.task.flow.enums.FlowTaskStatus;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 有限集任务，注意，有限集任务节点最终需要构成一个有向无环图
 * 
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
@Getter
@Setter
@ToString
public class SetTaskModel implements FlowTaskModel {

    /**
     * 任务幂等请求ID，该ID需要全局唯一
     */
    @Size(max = 200)
    @NotBlank
    private String requestId;

    /**
     * 第一个任务
     */
    @Valid
    @NotNull
    private TaskNodeModel firstTask;

    /**
     * 最后一个任务
     */
    @Valid
    @NotNull
    private TaskNodeModel lastTask;

    /**
     * 任务状态，查询时返回，创建时无需设置，不会消费
     */
    private FlowTaskStatus status;

}
