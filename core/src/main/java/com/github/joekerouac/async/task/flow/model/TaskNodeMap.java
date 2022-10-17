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

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

import com.github.joekerouac.async.task.entity.common.DatabaseObj;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 节点关系表，允许一个子节点对多个父节点、多个子节点对一个父节点
 * 
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
@Getter
@Setter
@ToString
public class TaskNodeMap extends DatabaseObj {

    /**
     * 任务 request ID
     */
    @Size(max = 200)
    @NotBlank
    private String taskRequestId;

    /**
     * 父节点 request ID
     */
    @Size(max = 200)
    @NotBlank
    private String parentNode;

    /**
     * 子节点 request ID
     */
    @Size(max = 200)
    @NotBlank
    private String childNode;

}
