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
package com.github.joekerouac.async.task.flow.test;

import java.util.List;

import com.github.joekerouac.async.task.flow.enums.StrategyResult;
import com.github.joekerouac.async.task.flow.enums.TaskNodeStatus;
import com.github.joekerouac.async.task.flow.model.TaskNode;
import com.github.joekerouac.async.task.flow.spi.ExecuteStrategy;
import com.github.joekerouac.async.task.starter.flow.annotations.Strategy;

/**
 * 测试策略，当父任务挂起的时候执行，当父任务正常的时候挂起
 * 
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
@Strategy(name = "TestStrategy")
public class TestExecuteStrategy implements ExecuteStrategy {

    public static final String NAME = "TestStrategy";

    @Override
    public StrategyResult process(final String nodeRequestId, final List<TaskNode> parents, final String context) {
        for (final TaskNode parent : parents) {
            if (parent.getStatus() != TaskNodeStatus.PENDING) {
                return StrategyResult.UNKNOWN;
            }
        }

        return StrategyResult.RUNNING;
    }
}
