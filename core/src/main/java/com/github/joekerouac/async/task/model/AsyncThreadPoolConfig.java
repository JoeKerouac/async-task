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
package com.github.joekerouac.async.task.model;

import javax.validation.constraints.Min;

import lombok.Data;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
@Data
public class AsyncThreadPoolConfig {

    /**
     * 核心线程池大小
     */
    @Min(value = 1, message = "核心线程池大小不能小于1")
    private int corePoolSize = 10;

    /**
     * 工作线程名，允许为空，默认async-worker
     */
    private String threadName;

    /**
     * 默认线程上下文类加载器，允许为空；
     */
    private ClassLoader defaultContextClassLoader;

}
