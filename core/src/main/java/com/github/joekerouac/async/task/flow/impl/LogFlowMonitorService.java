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
package com.github.joekerouac.async.task.flow.impl;

import com.github.joekerouac.common.tools.log.LoggerFactory;
import com.github.joekerouac.common.tools.log.Logger;
import com.github.joekerouac.async.task.flow.enums.FlowTaskStatus;
import com.github.joekerouac.async.task.flow.enums.TaskNodeStatus;
import com.github.joekerouac.async.task.flow.spi.FlowMonitorService;

/**
 * 流式任务监控的简单实现，日志监控
 * 
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public class LogFlowMonitorService implements FlowMonitorService {

    private static final Logger LOGGER = LoggerFactory.getLogger("FLOW_MONITOR");

    @Override
    public void deserializationError(final String requestId, final String task, final Object processor,
        final Throwable throwable) {
        LOGGER.error(throwable, "任务反序列化异常，请尽快人工介入，nodeRequestId=[{}], processor=[{}], task=[{}]", requestId, processor,
            task);
    }

    @Override
    public void nodeStatusAssertError(final String nodeRequestId, final TaskNodeStatus expectStatus,
        final TaskNodeStatus realStatus) {
        LOGGER.error("节点状态断言异常，节点状态不符合我们的期望，通常是由于执行策略问题导致的，或者是人工修改数据，请尽快人工介入, "
            + "nodeRequest=[{}], expectStatus=[{}], realStatus=[{}]", nodeRequestId, expectStatus, realStatus);
    }

    @Override
    public void nodeFinish(final String taskRequestId, final String nodeRequestId, final TaskNodeStatus status) {
        LOGGER.info("task node执行完毕， taskRequestId=[{}], nodeRequestId=[{}], status=[{}]", taskRequestId, nodeRequestId,
            status);
    }

    @Override
    public void taskFinish(final String taskRequestId, final FlowTaskStatus status) {
        LOGGER.info("flow task执行完毕， taskRequestId=[{}], status=[{}]", taskRequestId, status);
    }

    @Override
    public void nodeNotFound(final String asyncTaskRequestId, final String nodeRequestId) {
        LOGGER.error("指定task node找不到，可能是数据库被删除了，请尽快人工介入, asyncTaskRequestId=[{}], nodeRequestId=[{}]",
            asyncTaskRequestId, nodeRequestId);
    }

    @Override
    public void processorNotFound(final String nodeRequestId, final String processor) {
        LOGGER.error("指定处理器找不到，可能是代码被修改了，请尽快人工介入, nodeRequestId=[{}], processor=[{}]", nodeRequestId, processor);
    }
}
