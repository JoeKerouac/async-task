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
package com.github.joekerouac.async.task.starter.flow.config;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "flow.service")
public class FlowServiceConfigModel {

    /**
     * 流式任务节点关系构建参数，一次最多对多少个流式任务进行节点关系构建，设置过大可能会导致内存问题，如果不了解原理请勿修改默认值；
     */
    @Min(1)
    @Max(100)
    private int flowTaskBatchSize = 10;

    /**
     * 对于流式任务，一次构建多少个节点关系；
     *
     * PS：一般情况下不建议设置太大，因为在某些场景下可能因为某些任务数特别多的流式任务导致阻塞其他所有任务的正常执行，也不建议设置太小，否则可能影响性能；除非深入了解过处理原理，否则不建议修改默认值；
     */
    @Min(10)
    private int streamNodeMapBatchSize = 200;

}
