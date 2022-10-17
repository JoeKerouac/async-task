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

import com.github.joekerouac.async.task.flow.enums.StrategyResult;
import com.github.joekerouac.async.task.flow.enums.TaskNodeStatus;
import com.github.joekerouac.async.task.flow.model.TaskNode;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public class AllParentFinishExecuteStrategy extends AbstractExecuteStrategy {

    @Override
    public StrategyResult process(final String nodeRequestId, final List<TaskNode> parents, final String context) {
        Map<TaskNodeStatus, Integer> statusCount = getStatusCount(parents, context);
        Integer pendingCount = statusCount.getOrDefault(TaskNodeStatus.PENDING, 0);
        if (pendingCount > 0) {
            return StrategyResult.PENDING;
        }

        Integer successCount = statusCount.getOrDefault(TaskNodeStatus.SUCCESS, 0);
        Integer errorCount = statusCount.getOrDefault(TaskNodeStatus.ERROR, 0);
        if ((successCount + errorCount) == parents.size()) {
            return StrategyResult.RUNNING;
        }

        return StrategyResult.UNKNOWN;
    }

}
