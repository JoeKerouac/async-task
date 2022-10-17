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
package com.github.joekerouac.async.task.flow.spi;

import java.util.List;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;

import com.github.joekerouac.async.task.flow.enums.StrategyResult;
import com.github.joekerouac.async.task.flow.model.TaskNode;

/**
 * 节点执行策略
 *
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public interface ExecuteStrategy {

    /**
     * 节点执行策略
     * 
     * @param nodeRequestId
     *            当前待决策的节点requestId
     * @param parents
     *            当前待决策的节点的所有父节点
     * @param context
     *            策略上下文，允许为null
     * @return 决策结果，注意，如果决策结果不是{@link StrategyResult#UNKNOWN}，那么这个方法后续返回不应该更改，例如本次决策结果是{@link StrategyResult#PENDING}，
     *         那么后续除非人工介入修改数据，否则不允许更改决策结果；
     */
    StrategyResult process(@NotBlank String nodeRequestId, @NotEmpty List<TaskNode> parents, String context);

    /**
     * 校验执行策略上下文是否符合要求，如果节点执行策略的上下文不能为空，并且有格式要求，可以自行实现该方法用于校验；
     * 
     * @param parents
     *            节点的所有父节点
     * @param context
     *            节点执行策略上下文
     * @return true表示符合要求
     */
    default boolean checkContext(@NotEmpty List<TaskNode> parents, String context) {
        return true;
    }

}
