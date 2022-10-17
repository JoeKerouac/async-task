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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.github.joekerouac.common.tools.constant.ExceptionProviderConst;
import com.github.joekerouac.common.tools.string.StringUtils;
import com.github.joekerouac.common.tools.util.Assert;
import com.github.joekerouac.async.task.flow.enums.StrategyResult;
import com.github.joekerouac.async.task.flow.enums.TaskNodeStatus;
import com.github.joekerouac.async.task.flow.model.TaskNode;

/**
 * 子节点在指定requestId的父级全部执行成功时才能执行，如果有一个父级pending或者error，那么节点都会pending
 * 
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public class SpecialParentExecuteStrategy extends AbstractExecuteStrategy {

    @Override
    public StrategyResult process(final String nodeRequestId, final List<TaskNode> parents, final String context) {
        Assert.notBlank(context, StringUtils.format("指定节点全部成功执行策略参数为空, [{}]", nodeRequestId),
            ExceptionProviderConst.IllegalArgumentExceptionProvider);

        Set<String> parentRequestIds = Arrays.stream(context.split("\\,")).filter(StringUtils::isNotBlank)
            .map(String::trim).collect(Collectors.toSet());
        // 将指定的父级过滤出来
        List<TaskNode> specialParent = new ArrayList<>(parentRequestIds.size());
        for (final TaskNode parent : parents) {
            if (parentRequestIds.contains(parent.getRequestId())) {
                specialParent.add(parent);
            }
        }

        Map<TaskNodeStatus, Integer> statusCount = getStatusCount(specialParent, context);
        Integer pendingCount = statusCount.getOrDefault(TaskNodeStatus.PENDING, 0);
        Integer errorCount = statusCount.getOrDefault(TaskNodeStatus.PENDING, 0);
        if ((pendingCount + errorCount) > 0) {
            return StrategyResult.PENDING;
        }

        Integer successCount = statusCount.getOrDefault(TaskNodeStatus.SUCCESS, 0);
        if (successCount == specialParent.size()) {
            return StrategyResult.RUNNING;
        }

        return StrategyResult.UNKNOWN;
    }

    @Override
    public boolean checkContext(final List<TaskNode> parents, final String context) {
        if (!super.checkContext(parents, context)) {
            return false;
        }

        if (StringUtils.isBlank(context)) {
            return false;
        }

        if (parents == null || parents.isEmpty()) {
            return false;
        }

        Set<String> allRequestIds = parents.stream().map(TaskNode::getRequestId).collect(Collectors.toSet());

        String[] requiredRequestIds = context.split("\\,");
        for (final String requestId : requiredRequestIds) {
            if (StringUtils.isBlank(requestId)) {
                continue;
            }

            if (!allRequestIds.contains(requestId.trim())) {
                return false;
            }
        }

        return true;
    }

}
