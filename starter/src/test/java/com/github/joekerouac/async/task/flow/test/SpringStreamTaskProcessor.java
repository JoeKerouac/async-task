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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CountDownLatch;

import com.github.joekerouac.async.task.model.ExecResult;
import com.github.joekerouac.async.task.spi.AbstractAsyncTaskProcessor;
import com.github.joekerouac.async.task.starter.annotations.AsyncTaskProcessor;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
@AsyncTaskProcessor
public class SpringStreamTaskProcessor extends AbstractAsyncTaskProcessor<SpringTask> {

    public volatile CountDownLatch latch;

    public final List<SpringTask> contexts;

    private final Set<String> exec;

    public SpringStreamTaskProcessor() {
        this.exec = new ConcurrentSkipListSet<>();
        this.contexts = new ArrayList<>();
    }

    public void setLatch(CountDownLatch latch) {
        this.latch = latch;
    }

    @Override
    public ExecResult process(final String requestId, final SpringTask context, final Map<String, Object> cache)
        throws Throwable {
        latch.countDown();
        contexts.add(context);
        return ExecResult.ERROR;
    }

}
