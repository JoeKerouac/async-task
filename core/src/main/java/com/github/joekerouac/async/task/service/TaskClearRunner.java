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
package com.github.joekerouac.async.task.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.github.joekerouac.async.task.entity.AsyncTask;
import com.github.joekerouac.async.task.spi.AbstractAsyncTaskProcessor;
import com.github.joekerouac.async.task.spi.AsyncTaskRepository;
import com.github.joekerouac.async.task.spi.ProcessorRegistry;

import lombok.CustomLog;

/**
 * 任务清理服务，注意，该服务需要是daemon的，否则会有问题
 *
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
@CustomLog
public class TaskClearRunner extends AbstractClearRunner {

    /**
     * 批量清理的时候一次捞取的条数
     */
    private static final int LOAD_SIZE = 100;

    /**
     * 异步任务仓库
     */
    private final AsyncTaskRepository asyncTaskRepository;

    private final ProcessorRegistry processorRegistry;

    public TaskClearRunner(final AsyncTaskRepository asyncTaskRepository, ProcessorRegistry processorRegistry) {
        this.asyncTaskRepository = asyncTaskRepository;
        this.processorRegistry = processorRegistry;
    }

    /**
     * 清除指定状态的数据
     */
    protected void clear() {
        Set<String> allTaskType = processorRegistry.getAllTaskType();
        if (allTaskType.isEmpty()) {
            return;
        }

        Map<String, Integer> clearDescMap = new HashMap<>();
        for (String taskType : allTaskType) {
            AbstractAsyncTaskProcessor<Object> processor = processorRegistry.getProcessor(taskType);
            if (processor != null) {
                clearDescMap.put(taskType, processor.reserve());
            }
        }

        clearDescMap.forEach((processor, reserve) -> {
            boolean hasNext = true;

            while (hasNext) {
                final LocalDateTime endTime = LocalDateTime.now().plus(-1L * reserve, ChronoUnit.HOURS);
                // 注意，这里只清理执行成功的
                final List<AsyncTask> asyncTasks =
                    asyncTaskRepository.selectFinishPage(processor, endTime, 0, LOAD_SIZE);
                if (asyncTasks.isEmpty()) {
                    return;
                }

                final int delete = asyncTaskRepository
                    .delete(asyncTasks.stream().map(AsyncTask::getRequestId).collect(Collectors.toSet()));

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("当前要删除 [{}] 条数据，实际删除 [{}]条，当前任务清理说明: [{}:{}]， 当前要删除的数据列表： [{}]", asyncTasks.size(),
                        delete, processor, reserve, asyncTasks);
                }
                hasNext = asyncTasks.size() == LOAD_SIZE;
            }
        });
    }
}
