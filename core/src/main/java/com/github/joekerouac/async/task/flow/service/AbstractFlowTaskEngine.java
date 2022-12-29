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
package com.github.joekerouac.async.task.flow.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.joekerouac.async.task.AsyncTaskService;
import com.github.joekerouac.async.task.db.TransUtil;
import com.github.joekerouac.async.task.flow.AbstractFlowProcessor;
import com.github.joekerouac.async.task.flow.enums.FailStrategy;
import com.github.joekerouac.async.task.flow.enums.StrategyResult;
import com.github.joekerouac.async.task.flow.enums.TaskNodeStatus;
import com.github.joekerouac.async.task.flow.model.TaskNode;
import com.github.joekerouac.async.task.flow.spi.*;
import com.github.joekerouac.async.task.model.ExecResult;
import com.github.joekerouac.async.task.model.TaskFinishCode;
import com.github.joekerouac.async.task.model.TransStrategy;
import com.github.joekerouac.async.task.spi.AbstractAsyncTaskProcessor;
import com.github.joekerouac.async.task.spi.ConnectionSelector;
import com.github.joekerouac.common.tools.constant.ExceptionProviderConst;
import com.github.joekerouac.common.tools.string.StringUtils;
import com.github.joekerouac.common.tools.util.Assert;

import lombok.Builder;
import lombok.CustomLog;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 有限集任务执行引擎，核心处理逻辑都在{@link #afterProcess(String, String, TaskFinishCode, Throwable, Map)}上
 *
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
@CustomLog
public abstract class AbstractFlowTaskEngine extends AbstractAsyncTaskProcessor<String> {

    /**
     * taskNode缓存
     */
    private static final String TASK_NODE_CACHE_KEY = "taskNode";

    /**
     * 表示task是否执行的标志缓存，true表示task执行了
     */
    private static final String TASK_EXECUTE_FLAG_CACHE_KEY = "taskExecute";

    /**
     * 任务对应的真实处理器缓存key
     */
    private static final String TASK_PROCESSOR_CACHE_KEY = "processor";

    /**
     * 节点任务数据缓存key
     */
    private static final String TASK_DATA_CACHE_KEY = "taskData";

    /**
     * 任务处理缓存的缓存key
     */
    private static final String TASK_CACHE_CACHE_KEY = "taskCache";

    /**
     * 所有任务处理器
     */
    protected final Map<String, AbstractFlowProcessor<?>> processors;

    /**
     * 异步任务服务
     */
    protected final AsyncTaskService asyncTaskService;

    /**
     * 流监控任务
     */
    protected final FlowMonitorService flowMonitorService;

    /**
     * 节点任务仓库
     */
    protected final FlowTaskRepository flowTaskRepository;

    /**
     * 节点仓库
     */
    protected final TaskNodeRepository taskNodeRepository;

    /**
     * 节点关系仓库
     */
    protected final TaskNodeMapRepository taskNodeMapRepository;

    /**
     * 所有执行策略
     */
    protected final Map<String, ExecuteStrategy> executeStrategies;

    /**
     * 链接选择器
     */

    public AbstractFlowTaskEngine(EngineConfig config) {
        this.processors = config.processors;
        this.asyncTaskService = config.asyncTaskService;
        this.flowMonitorService = config.flowMonitorService;
        this.flowTaskRepository = config.flowTaskRepository;
        this.taskNodeRepository = config.taskNodeRepository;
        this.taskNodeMapRepository = config.taskNodeMapRepository;
        this.executeStrategies = config.executeStrategies;
    }

    /**
     * 获取当前节点的所有子节点，如果没有子节点，就调用{@link #taskFinish(TaskNode, TaskNodeStatus)}结束处理
     *
     * @param currentNode
     *            当前执行节点
     * @return 获取当前节点的所有子节点
     */
    protected List<TaskNode> getAllChild(TaskNode currentNode) {
        // 找到当前节点的所有子节点
        List<String> allChild =
            taskNodeMapRepository.getAllChild(currentNode.getTaskRequestId(), currentNode.getRequestId());
        if (allChild.isEmpty()) {
            return Collections.emptyList();
        }

        // 查询出来所有子节点
        List<TaskNode> childNodeList = taskNodeRepository.selectByRequestIds(allChild);
        Assert.assertTrue(childNodeList != null && childNodeList.size() == allChild.size(),
            StringUtils.format("当前根据child request id查询出来的节点数量与id数量不一致，请核查, [{}, {}]", allChild, childNodeList),
            ExceptionProviderConst.IllegalStateExceptionProvider);
        return childNodeList;
    }

    /**
     * 流式任务当前所有节点任务都处理完毕，结束任务
     * 
     * @param currentNode
     *            当前执行节点
     * @param taskNodeStatus
     *            当前节点状态
     */
    protected abstract void taskFinish(TaskNode currentNode, TaskNodeStatus taskNodeStatus);

    /**
     * 通知某个节点需要pending
     * 
     * @param notifyNode
     *            触发通知的节点
     * @param pendingNode
     *            需要pending的节点
     */
    protected abstract void notifyPending(TaskNode notifyNode, TaskNode pendingNode);

    /**
     * 决策节点当前的状态
     *
     * @param notifyRequestId
     *            通知进行该决策的节点的requestId，允许为null
     * @param node
     *            待决策节点
     * @return 决策结果
     */
    public StrategyResult decideNodeStatus(String notifyRequestId, TaskNode node) {
        String executeStrategyBeanName = node.getExecuteStrategy();
        ExecuteStrategy executeStrategy = executeStrategies.get(executeStrategyBeanName);
        Assert.assertTrue(executeStrategy != null,
            StringUtils.format("指定执行策略 [{}] 不存在， 当前策略集合： [{}]", executeStrategyBeanName, executeStrategies.keySet()),
            ExceptionProviderConst.IllegalStateExceptionProvider);

        // 查出来所有父节点，肯定不为空，因为至少包含当前执行的节点，注意，这里可能会有并发问题，就是多个父节点同时执行完毕，可能有部分父节点
        // 在唤醒该子节点的时候会出现其他父节点其实已经执行完毕了，但是我们这里获取到的还没有执行完，这个不影响，因为那个比较慢的父节点对应的
        // 唤醒任务检查会成功；
        List<String> allParent = taskNodeMapRepository.getAllParent(node.getTaskRequestId(), node.getRequestId());

        List<TaskNode> parentNodeList;
        if (allParent.isEmpty()) {
            parentNodeList = Collections.emptyList();
        } else {
            parentNodeList = taskNodeRepository.selectByRequestIds(allParent);
        }
        Assert.assertTrue(parentNodeList != null && parentNodeList.size() == allParent.size(),
            StringUtils.format("当前根据parent request id查询出来的节点数量与id数量不一致，请核查, [{}, {}]", allParent, parentNodeList),
            ExceptionProviderConst.IllegalStateExceptionProvider);
        // 使用用户自定义的执行策略来判断子节点是否可以执行
        StrategyResult result = executeStrategy.process(node.getRequestId(), parentNodeList, node.getStrategyContext());

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("节点 [{}] 执行完毕触发子节点 [{}] 状态检查，子节点当前是WAIT状态，执行策略 [{}] 给定的结果是 [{}]， 当前子节点的所有父节点为：[{}]",
                notifyRequestId, node.getRequestId(), executeStrategy, result, parentNodeList);
        }
        return result;
    }

    @Override
    public String deserialize(final String requestId, final String context, final Map<String, Object> cache) {
        return super.deserialize(requestId, context, cache);
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean canRetry(final String requestId, final String nodeRequestId, final Throwable throwable,
        final Map<String, Object> cache) {
        // 代理到用户的流式任务处理器上
        Map<String, Object> flowCache = (Map<String, Object>)cache.get(TASK_CACHE_CACHE_KEY);
        TaskNode taskNode = (TaskNode)cache.get(TASK_NODE_CACHE_KEY);
        AbstractFlowProcessor<Object> processor = (AbstractFlowProcessor<Object>)cache.get(TASK_PROCESSOR_CACHE_KEY);
        return processor == null || processor.canRetry(nodeRequestId, taskNode.getNodeData(), throwable, flowCache);
    }

    @SuppressWarnings("unchecked")
    @Override
    public long nextExecTimeInterval(final String requestId, final int retry, final String nodeRequestId,
        final Map<String, Object> cache) {
        // 代理到用户的流式任务处理器上
        Map<String, Object> flowCache = (Map<String, Object>)cache.get(TASK_CACHE_CACHE_KEY);
        TaskNode taskNode = (TaskNode)cache.get(TASK_NODE_CACHE_KEY);
        AbstractFlowProcessor<Object> processor = (AbstractFlowProcessor<Object>)cache.get(TASK_PROCESSOR_CACHE_KEY);
        return processor == null ? super.nextExecTimeInterval(requestId, retry, nodeRequestId, cache)
            : processor.nextExecTimeInterval(nodeRequestId, retry, taskNode.getNodeData(), flowCache);
    }

    @Override
    public ExecResult process(final String requestId, final String nodeRequestId, final Map<String, Object> cache)
        throws Throwable {
        Map<String, Object> flowCache = new HashMap<>();
        cache.put(TASK_CACHE_CACHE_KEY, flowCache);

        TaskNode taskNode = taskNodeRepository.selectByRequestId(nodeRequestId);
        Assert.notNull(taskNode, StringUtils.format("系统异常，无法根据nodeRequestId [{}] 找到节点任务", nodeRequestId),
            ExceptionProviderConst.IllegalStateExceptionProvider);
        Assert.assertTrue(taskNode.getStatus() != TaskNodeStatus.INIT,
            StringUtils.format("当前任务 [{}:{}] 状态异常", nodeRequestId, taskNode),
            ExceptionProviderConst.IllegalStateExceptionProvider);
        cache.put(TASK_NODE_CACHE_KEY, taskNode);

        if (taskNode.getStatus() == TaskNodeStatus.WAIT) {
            LOGGER.warn("当前任务 [{}] 还未ready，提前进入了执行，当前中断执行，继续等待", nodeRequestId);
            return ExecResult.WAIT;
        }

        // READY表示异步任务就绪后还从未执行过，RUNNING表示异步任务已经执行了，但是可能中途被中断了，中断后用户又重新修改异步任务状态将其调起
        // 了，或者是任务重试了，其他状态表示当前已经执行完毕，就无需在执行了
        if (taskNode.getStatus() == TaskNodeStatus.READY || taskNode.getStatus() == TaskNodeStatus.RUNNING) {
            // 注意，flag要放在最前边，防止其他语句执行异常导致放入失败导致后续判断错误
            cache.put(TASK_EXECUTE_FLAG_CACHE_KEY, Boolean.TRUE);

            @SuppressWarnings("unchecked")
            AbstractFlowProcessor<Object> processor =
                (AbstractFlowProcessor<Object>)processors.get(taskNode.getProcessor());
            // 注意，找不到处理器时抛出异常，等待重试，因为可能时发布过程中的不兼容问题导致的，可能给个机会调度到最新代码的机器上就可以执行了
            Assert.notNull(processor,
                StringUtils.format("任务 [{}] 对应的处理器 [{}] 不存在", nodeRequestId, taskNode.getProcessor()),
                ExceptionProviderConst.UnsupportedOperationExceptionProvider);
            cache.put(TASK_PROCESSOR_CACHE_KEY, processor);

            // 调用反序列化逻辑，这里反序列化异常和processor处理一样，即等待重试，因为这个也可能是因为发布过程中的兼容问题导致的
            Object data = processor.deserialize(nodeRequestId, taskNode.getNodeData(), flowCache);
            cache.put(TASK_DATA_CACHE_KEY, data);

            // 如果还是READY状态，先将其修改为RUNNING状态
            if (taskNode.getStatus() == TaskNodeStatus.READY) {
                taskNodeRepository.updateStatus(nodeRequestId, TaskNodeStatus.RUNNING);
                taskNode.setStatus(TaskNodeStatus.RUNNING);
            }

            // 处理任务，注意，这里可能有异常，不过我们不用处理，等待异步任务框架处理重试即可
            processor.process(nodeRequestId, data, flowCache);
        }

        return ExecResult.SUCCESS;
    }

    @Override
    public void afterProcess(final String requestId, final String nodeRequestId, final TaskFinishCode code,
        final Throwable processException, final Map<String, Object> cache) {
        // 注意，虽然本方法父级方法要求尽量不要抛出异常，但是我们这个方法抛出异常几乎不会有影响，因为我们的任务执行有幂等控制，任务执行一次后不会
        // 再次执行，所以即使抛出异常导致重试问题也不大；
        TaskNode taskNode = (TaskNode)cache.get(TASK_NODE_CACHE_KEY);
        TaskFinishCode realCode = code;

        if (taskNode == null) {
            // 一般不可能，除非是手动删除了task node表，如果taskNode都找不到，那就没办法进行后续工作了，所以就直接返回就行，但是这个注意监控，
            // 这个是一个严重问题
            flowMonitorService.nodeNotFound(requestId, nodeRequestId);
            return;
        }

        Boolean executeFlag = (Boolean)cache.get(TASK_EXECUTE_FLAG_CACHE_KEY);

        // 优先修改任务节点的状态，这样即使后续任务被重新调起我们也能最大程度避免重复执行
        TaskNodeStatus status = taskNode.getStatus();

        // 如果本轮实际执行了任务，那么先修改任务状态为终态
        if (executeFlag == Boolean.TRUE) {
            @SuppressWarnings("unchecked")
            AbstractFlowProcessor<Object> processor =
                (AbstractFlowProcessor<Object>)cache.get(TASK_PROCESSOR_CACHE_KEY);

            // 注意，processor为null时不调用用户的afterProcess，但是需要处理流式任务
            if (processor == null) {
                // 注意，因为这里是我们的引擎包裹了一层，所以code肯定不是正确的，我们这里将其修改为正确的code
                realCode = TaskFinishCode.NO_PROCESSOR;
                flowMonitorService.processorNotFound(nodeRequestId, taskNode.getProcessor());
            }

            Object data = cache.get(TASK_DATA_CACHE_KEY);
            // 如果processor等于null，那么data肯定等于null，此时不用走这里
            if (processor != null && data == null) {
                realCode = TaskFinishCode.DESERIALIZATION_ERROR;
                flowMonitorService.deserializationError(taskNode.getRequestId(), taskNode.getNodeData(), processor,
                    null);
            }

            // 注意，我们要先调用流式任务的afterProcess，然后才能调用后续我们的逻辑，用户流式任务的afterProcess应该是优先于流式任务处理引擎的，
            // 只有用户流式任务的afterProcess执行完，整个流式任务才算执行完成，所以我们这里要先调用；
            // 另外我们在这里调用而不是在调用完process后直接调用是因为这样我们就不用去计算code到底是什么了（因为异步任务是可以重试的）；
            if (processor != null && data != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> flowCache = (Map<String, Object>)cache.get(TASK_CACHE_CACHE_KEY);
                processor.afterProcess(nodeRequestId, data, realCode, processException, flowCache);
            }

            FailStrategy failStrategy = taskNode.getFailStrategy();

            switch (realCode) {
                case SUCCESS:
                    status = TaskNodeStatus.SUCCESS;
                    break;
                case USER_ERROR:
                case CANNOT_RETRY:
                case NO_PROCESSOR:
                case RETRY_OVERFLOW:
                case DESERIALIZATION_ERROR:
                    switch (failStrategy) {
                        case PENDING:
                            status = TaskNodeStatus.PENDING;
                            break;
                        case IGNORE:
                            status = TaskNodeStatus.ERROR;
                            break;
                        default:
                            throw new IllegalStateException(StringUtils.format("不支持的失败策略：[{}]", failStrategy));
                    }
                    break;
                default:
                    throw new IllegalStateException(StringUtils.format("不支持的结束码：[{}]", realCode));
            }

            taskNodeRepository.updateStatus(taskNode.getRequestId(), status);
            flowMonitorService.nodeFinish(taskNode.getTaskRequestId(), taskNode.getRequestId(), status);
            taskNode.setStatus(status);
        }

        // 执行节点结束回调
        nodeFinish(taskNode, status);
    }

    /**
     * 节点执行完毕回调
     * 
     * @param taskNode
     *            执行完毕的节点
     * @param taskNodeStatus
     *            节点状态
     */
    protected void nodeFinish(TaskNode taskNode, TaskNodeStatus taskNodeStatus) {
        // 找到当前节点的所有子节点
        List<TaskNode> childNodeList = getAllChild(taskNode);

        // 执行结束，判断节点是否尾节点，如果是尾节点，处理完毕后直接结束
        if (childNodeList.isEmpty()) {
            taskFinish(taskNode, taskNodeStatus);
            return;
        }

        // 开始check子节点是否可以执行
        for (final TaskNode childNode : childNodeList) {
            notifyChild(taskNode, childNode);
        }
    }

    /**
     * 唤醒子节点，对子节点状态进行检查，如果可以执行就调度执行
     * 
     * @param notifyNode
     *            触发唤醒操作的节点，同时也是被唤醒节点的父节点
     * @param wakeUpNode
     *            要唤醒的子节点
     */
    protected void notifyChild(TaskNode notifyNode, TaskNode wakeUpNode) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("节点 [{}] 的子节点 [{}] 当前状态为： [{}]", notifyNode.getRequestId(), wakeUpNode.getRequestId(),
                wakeUpNode.getStatus());
        }

        if (wakeUpNode.getStatus() == TaskNodeStatus.WAIT) {
            StrategyResult result = decideNodeStatus(notifyNode.getRequestId(), wakeUpNode);

            switch (result) {
                case UNKNOWN:
                    // 未知状态，说明当前条件不够，可能是有多个父节点，只有本节点执行成功了，注意，这里多个父节点并发该子节点没有问题，因为每个
                    // 父节点都是先更新自己状态，然后才遍历子节点的，所以如果多个父节点并发完成，那多个父节点间肯定至少有一个查询的时候是满足状态的；
                    break;
                case PENDING:
                    // pending状态比较特殊，要根据任务类型来选择不同的操作
                    notifyPending(notifyNode, wakeUpNode);
                    break;
                case RUNNING:
                    // 注意，这里一定要用事务，因为如果节点状态修改成功，但是异步任务唤醒失败时会导致异步任务永远不会有唤醒的机会了
                    TransUtil.run(TransStrategy.REQUIRED, () -> {
                        // 将节点状态从wait修改为ready，这里修改失败不用管，可能是当前待唤醒节点已经处理过了
                        taskNodeRepository.casUpdateStatus(wakeUpNode.getRequestId(), TaskNodeStatus.WAIT,
                            TaskNodeStatus.READY);
                        // 唤醒该子节点，这个对子节点可能并发，不过不影响，唤醒是支持并发的，只会唤醒一次
                        asyncTaskService.notifyTask(wakeUpNode.getRequestId());
                    });
                    break;
                default:
                    throw new IllegalStateException(StringUtils.format("不支持的状态： [{}]", result));
            }
        } else {
            LOGGER.info("当前节点 [{}] 已经是 [{}] 状态了，无需处理", wakeUpNode.getRequestId(), wakeUpNode.getStatus());
        }
    }

    @Data
    @Builder
    @Accessors(chain = true, fluent = true)
    public static class EngineConfig {

        private Map<String, AbstractFlowProcessor<?>> processors;

        private AsyncTaskService asyncTaskService;

        private FlowMonitorService flowMonitorService;

        private FlowTaskRepository flowTaskRepository;

        private TaskNodeRepository taskNodeRepository;

        private TaskNodeMapRepository taskNodeMapRepository;

        private Map<String, ExecuteStrategy> executeStrategies;

        private ConnectionSelector connectionSelector;
    }

}
