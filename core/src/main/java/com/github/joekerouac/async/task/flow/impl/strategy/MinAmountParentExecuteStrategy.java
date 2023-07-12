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

import java.util.List;
import java.util.Map;

import com.github.joekerouac.common.tools.string.StringUtils;
import com.github.joekerouac.async.task.flow.enums.StrategyResult;
import com.github.joekerouac.async.task.flow.enums.TaskNodeStatus;
import com.github.joekerouac.async.task.flow.model.TaskNode;

/**
 * 当父节点执行成功数量达到我们设置的值后就可以执行了
 *
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public class MinAmountParentExecuteStrategy extends AbstractExecuteStrategy {

    @Override
    public StrategyResult process(final String nodeRequestId, final List<TaskNode> parents, final String context) {
        Map<TaskNodeStatus, Integer> statusCount = getStatusCount(parents, context);
        Integer successCount = statusCount.getOrDefault(TaskNodeStatus.SUCCESS, 0);
        int requiredSuccessCount = Integer.parseInt(context);
        if (successCount >= requiredSuccessCount) {
            return StrategyResult.RUNNING;
        }

        Integer errorCount = statusCount.getOrDefault(TaskNodeStatus.ERROR, 0);
        Integer pendingCount = statusCount.getOrDefault(TaskNodeStatus.PENDING, 0);
        // 如果总量减去已经失败的数量小于我们要求的最小成功数，那么直接挂起就行
        if ((parents.size() - (errorCount + pendingCount)) < requiredSuccessCount) {
            return StrategyResult.PENDING;
        }

        return StrategyResult.UNKNOWN;
    }

    @Override
    public boolean checkContext(final List<TaskNode> parents, final String context) {
        if (StringUtils.isBlank(context)) {
            return false;
        }
        try {
            int count = Integer.parseInt(context);
            return count >= 0 && count <= parents.size();
        } catch (Throwable throwable) {
            return false;
        }
    }
}
