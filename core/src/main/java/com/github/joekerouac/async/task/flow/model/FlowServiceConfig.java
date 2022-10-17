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
package com.github.joekerouac.async.task.flow.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import com.github.joekerouac.async.task.AsyncTaskService;
import com.github.joekerouac.async.task.flow.AbstractFlowProcessor;
import com.github.joekerouac.async.task.flow.impl.StrategyConst;
import com.github.joekerouac.async.task.flow.impl.strategy.AllParentFinishExecuteStrategy;
import com.github.joekerouac.async.task.flow.impl.strategy.AllParentSuccessExecuteStrategy;
import com.github.joekerouac.async.task.flow.impl.strategy.MinAmountParentExecuteStrategy;
import com.github.joekerouac.async.task.flow.impl.strategy.SpecialParentExecuteStrategy;
import com.github.joekerouac.async.task.flow.spi.ExecuteStrategy;
import com.github.joekerouac.async.task.flow.spi.FlowMonitorService;
import com.github.joekerouac.async.task.flow.spi.FlowTaskRepository;
import com.github.joekerouac.async.task.flow.spi.TaskNodeMapRepository;
import com.github.joekerouac.async.task.flow.spi.TaskNodeRepository;
import com.github.joekerouac.async.task.spi.ConnectionSelector;
import com.github.joekerouac.async.task.spi.IDGenerator;
import com.github.joekerouac.async.task.spi.TransactionHook;

import lombok.CustomLog;
import lombok.Data;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
@CustomLog
@Data
public class FlowServiceConfig {

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

    /**
     * ID生成器
     */
    @NotNull
    private IDGenerator idGenerator;

    /**
     * 事务拦截器，允许为空，为空时可能小概率出现一些问题，例如任务已经执行了，但是添加数据库失败
     */
    private TransactionHook transactionHook;

    /**
     * 异步任务服务
     */
    @NotNull
    private AsyncTaskService asyncTaskService;

    /**
     * 流监控任务
     */
    @NotNull
    private FlowMonitorService flowMonitorService;

    /**
     * 节点任务仓库，如果为空时{@link #connectionSelector}不允许为空，系统将使用{@link #connectionSelector}来构建仓储服务
     */
    private FlowTaskRepository flowTaskRepository;

    /**
     * 节点仓库，如果为空时{@link #connectionSelector}不允许为空，系统将使用{@link #connectionSelector}来构建仓储服务
     */
    private TaskNodeRepository taskNodeRepository;

    /**
     * 节点关系仓库，如果为空时{@link #connectionSelector}不允许为空，系统将使用{@link #connectionSelector}来构建仓储服务
     */
    private TaskNodeMapRepository taskNodeMapRepository;

    /**
     * 链接选择器，注意，返回的链接中必须可以对flow_task、task_node、task_node_map三个表进行操作；如果三个repository其中某些为空某些不为空时，该
     * connectionSelector返回的链接必须与他们三个连接的时同一个库（或者透明的支持跨库事务）；
     */
    private ConnectionSelector connectionSelector;

    /**
     * 所有任务处理器
     */
    @NotNull
    @SuppressWarnings("rawtypes")
    private List<AbstractFlowProcessor> processors = new ArrayList<>();

    /**
     * 所有执行策略
     */
    @NotNull
    private Map<String, ExecuteStrategy> executeStrategies = new HashMap<>();

    public FlowServiceConfig() {
        // 注册默认执行策略
        executeStrategies.put(StrategyConst.ALL_PARENT_FINISH, new AllParentFinishExecuteStrategy());
        executeStrategies.put(StrategyConst.ALL_PARENT_SUCCESS_STRATEGY, new AllParentSuccessExecuteStrategy());
        executeStrategies.put(StrategyConst.MIN_AMOUNT_PARENT_STRATEGY, new MinAmountParentExecuteStrategy());
        executeStrategies.put(StrategyConst.SPECIAL_PARENT_STRATEGY, new SpecialParentExecuteStrategy());
    }
}