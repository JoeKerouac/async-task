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

/**
 * 处理器提供者，异步任务系统优先使用静态添加的processor，如果没有，则使用该提供器提供的processor，并将其加入静态processor列表
 *
 * @author JoeKerouac
 * @date 2023-01-06 11:28
 * @since 1.0.0
 */
public interface ProcessorSupplier {

    /**
     * 获取一个processor
     * 
     * @param processorName
     *            处理器名
     * @param <T>
     *            任务实际类型
     * @return processor
     */
    <T, P extends AbstractAsyncTaskProcessor<T>> P get(String processorName);

}
