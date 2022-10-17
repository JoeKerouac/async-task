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
package com.github.joekerouac.async.task.exception;

import com.github.joekerouac.async.task.spi.AbstractAsyncTaskProcessor;

import lombok.Getter;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public class ProcessorAlreadyExistException extends RuntimeException {

    /**
     * 注意，这个是当前已经存在的processor，不是本次要新添加的processor
     */
    @Getter
    private final AbstractAsyncTaskProcessor<?> processor;

    public ProcessorAlreadyExistException(final AbstractAsyncTaskProcessor<?> processor) {
        this.processor = processor;
    }

    public ProcessorAlreadyExistException(final String message, final AbstractAsyncTaskProcessor<?> processor) {
        super(message);
        this.processor = processor;
    }

    public ProcessorAlreadyExistException(final String message, final Throwable cause,
        final AbstractAsyncTaskProcessor<?> processor) {
        super(message, cause);
        this.processor = processor;
    }

    public ProcessorAlreadyExistException(final Throwable cause, final AbstractAsyncTaskProcessor<?> processor) {
        super(cause);
        this.processor = processor;
    }
}
