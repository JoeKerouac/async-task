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
package com.github.joekerouac.async.task.flow.impl.strategy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.joekerouac.common.tools.constant.ExceptionProviderConst;
import com.github.joekerouac.common.tools.string.StringUtils;
import com.github.joekerouac.common.tools.util.Assert;
import com.github.joekerouac.async.task.flow.enums.TaskNodeStatus;
import com.github.joekerouac.async.task.flow.model.TaskNode;
import com.github.joekerouac.async.task.flow.spi.ExecuteStrategy;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public abstract class AbstractExecuteStrategy implements ExecuteStrategy {

    /**
     * 获取所有父节点的状态计数
     * 
     * @param parents
     *            父节点
     * @param context
     *            策略上下文，用于校验
     * @return 指定父节点的每个状态的数量
     */
    protected Map<TaskNodeStatus, Integer> getStatusCount(final List<TaskNode> parents, final String context) {
        Assert.assertTrue(checkContext(parents, context), StringUtils.format("参数校验失败，可能是数据被篡改了"),
            ExceptionProviderConst.IllegalArgumentExceptionProvider);

        Map<TaskNodeStatus, Integer> resultMap = new HashMap<>();

        for (final TaskNode parent : parents) {
            TaskNodeStatus parentStatus = parent.getStatus();
            Integer counter = resultMap.get(parentStatus);
            if (counter == null) {
                counter = 0;
            }
            resultMap.put(parentStatus, counter + 1);
        }

        return resultMap;
    }
}
