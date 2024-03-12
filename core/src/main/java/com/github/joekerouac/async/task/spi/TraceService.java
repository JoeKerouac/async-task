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

import javax.validation.constraints.NotNull;

/**
 * trace服务，注意，对于同一个context，resume和finish可能会多次执行，即使finish时传入的retry为false也可能因为人工触发等原因再次重试
 *
 * @author JoeKerouac
 * @date 2023-03-01 14:54
 * @since 2.0.2
 */
public interface TraceService {

    /**
     * 新建trace
     *
     * @return trace上下文，在finish时传入
     */
    default Object newTrace() {
        // 默认不支持
        return null;
    }

    /**
     * 将当前trace上下文dump出来
     * 
     * @return trace上下文，返回null时将会认为当前没有trace上下文
     */
    String dump();

    /**
     * 将trace恢复到当前线程，同一个traceContext可能会多次resume、finish
     * 
     * @param retry
     *            第几次重试
     * @param traceContext
     *            traceContext
     * @return 上下文，会在finish时传入
     */
    Object resume(int retry, @NotNull String traceContext);

    /**
     * 结束当前trace
     *
     * @param context
     *            {@link #resume(int, String)}返回的上下文
     * @param retry
     *            是否还会重试
     * @param result
     *            结果
     * @param throwable
     *            异常
     */
    void finish(Object context, boolean retry, Object result, Throwable throwable);

}
