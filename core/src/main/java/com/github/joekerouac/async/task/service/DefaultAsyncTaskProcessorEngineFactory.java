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

import com.github.joekerouac.async.task.model.AsyncTaskProcessorEngineConfig;
import com.github.joekerouac.async.task.spi.AsyncTaskProcessorEngine;
import com.github.joekerouac.async.task.spi.AsyncTaskProcessorEngineFactory;

/**
 * @author JoeKerouac
 * @date 2023-04-18 15:56
 * @since 2.0.3
 */
public class DefaultAsyncTaskProcessorEngineFactory implements AsyncTaskProcessorEngineFactory {

    @Override
    public AsyncTaskProcessorEngine create(AsyncTaskProcessorEngineConfig engineConfig) {
        return new DefaultAsyncTaskProcessorEngine(engineConfig);
    }
}
