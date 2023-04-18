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
package com.github.joekerouac.async.task.spi;

import com.github.joekerouac.async.task.model.AsyncTaskProcessorEngineConfig;

/**
 * 异步任务引擎工厂
 * 
 * @author JoeKerouac
 * @date 2023-04-18 15:22
 * @since 2.0.3
 */
public interface AsyncTaskProcessorEngineFactory {

    /**
     * 创建一个异步任务执行引擎
     * 
     * @param engineConfig
     *            该引擎的执行配置
     * @return 异步任务引擎
     */
    AsyncTaskProcessorEngine create(AsyncTaskProcessorEngineConfig engineConfig);

}
