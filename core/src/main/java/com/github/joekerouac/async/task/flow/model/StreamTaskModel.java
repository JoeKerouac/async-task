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

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 流式任务，流式任务，流式任务的每个任务节点最多只有一个子节点，最多只有一个父节点，流式任务会串行执行，但是不保证多次添加的任务按照添加先后顺序执行；一个streamId表示一个流式任务；
 * 
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
@Getter
@Setter
@ToString
public class StreamTaskModel implements FlowTaskModel {

    /**
     * 流式任务的ID，对于streamId相同的任务认为是同一个流式任务链上的，对于streamId不同的任务则认为是不同流式任务链上的任务;
     * 
     * PS：如果涉及分库分表，那么该ID与所有子任务的分库分表位应该一致；
     */
    @Size(max = 100)
    @NotBlank
    private String streamId;

    /**
     * 第一个任务
     */
    @Valid
    @NotNull
    private TaskNodeModel firstTask;

}
