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
package com.github.joekerouac.async.task.impl;

import com.github.joekerouac.async.task.model.TaskQueueConfig;
import com.github.joekerouac.async.task.spi.AsyncTaskRepository;
import com.github.joekerouac.async.task.spi.TaskCacheQueue;
import com.github.joekerouac.async.task.spi.TaskCacheQueueFactory;

/**
 * @author JoeKerouac
 * @date 2023-11-11 14:13
 * @since 4.0.0
 */
public class DefaultTaskCacheQueueFactory implements TaskCacheQueueFactory {

    public TaskCacheQueue build(TaskQueueConfig config, AsyncTaskRepository repository) {
        return new DefaultTaskCacheQueue(config, repository);
    }

}
