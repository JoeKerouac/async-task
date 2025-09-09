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

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.github.joekerouac.async.task.AsyncTaskService;
import com.github.joekerouac.async.task.Const;
import com.github.joekerouac.async.task.flow.FlowService;
import com.github.joekerouac.async.task.flow.enums.FlowTaskStatus;
import com.github.joekerouac.async.task.flow.enums.FlowTaskType;
import com.github.joekerouac.async.task.flow.enums.StrategyResult;
import com.github.joekerouac.async.task.flow.enums.TaskNodeStatus;
import com.github.joekerouac.async.task.flow.impl.repository.FlowTaskRepositoryImpl;
import com.github.joekerouac.async.task.flow.impl.repository.TaskNodeMapRepositoryImpl;
import com.github.joekerouac.async.task.flow.impl.repository.TaskNodeRepositoryImpl;
import com.github.joekerouac.async.task.flow.model.FlowServiceConfig;
import com.github.joekerouac.async.task.flow.model.FlowTask;
import com.github.joekerouac.async.task.flow.model.FlowTaskModel;
import com.github.joekerouac.async.task.flow.model.SetTaskModel;
import com.github.joekerouac.async.task.flow.model.StreamTaskModel;
import com.github.joekerouac.async.task.flow.model.TaskNode;
import com.github.joekerouac.async.task.flow.model.TaskNodeMap;
import com.github.joekerouac.async.task.flow.model.TaskNodeModel;
import com.github.joekerouac.async.task.flow.spi.ExecuteStrategy;
import com.github.joekerouac.async.task.flow.spi.FlowTaskRepository;
import com.github.joekerouac.async.task.flow.spi.TaskNodeMapRepository;
import com.github.joekerouac.async.task.flow.spi.TaskNodeRepository;
import com.github.joekerouac.async.task.model.TransStrategy;
import com.github.joekerouac.async.task.spi.AbstractAsyncTaskProcessor;
import com.github.joekerouac.async.task.spi.AsyncTransactionManager;
import com.github.joekerouac.async.task.spi.IDGenerator;
import com.github.joekerouac.async.task.spi.ProcessorRegistry;
import com.github.joekerouac.async.task.spi.TraceService;
import com.github.joekerouac.common.tools.collection.CollectionUtil;
import com.github.joekerouac.common.tools.collection.Pair;
import com.github.joekerouac.common.tools.constant.ExceptionProviderConst;
import com.github.joekerouac.common.tools.db.SqlUtil;
import com.github.joekerouac.common.tools.exception.ExceptionUtil;
import com.github.joekerouac.common.tools.scheduler.SchedulerSystem;
import com.github.joekerouac.common.tools.scheduler.SchedulerTask;
import com.github.joekerouac.common.tools.scheduler.SimpleSchedulerTask;
import com.github.joekerouac.common.tools.scheduler.TaskDescriptor;
import com.github.joekerouac.common.tools.string.StringUtils;
import com.github.joekerouac.common.tools.util.Assert;
import com.github.joekerouac.common.tools.util.Starter;

import lombok.CustomLog;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
@CustomLog
public class FlowServiceImpl implements FlowService {

    /**
     * 对于流式任务，一次构建多少个节点关系；
     *
     * PS：一般情况下不建议设置太大，因为在某些场景下可能因为某些任务数特别多的流式任务导致阻塞其他所有任务的正常执行，也不建议设置太小，否则可能影响性能；除非深入了解过处理原理，否则不建议修改默认值；
     */
    private final int streamNodeMapBatchSize;

    /**
     * ID生成器
     */
    private final IDGenerator idGenerator;

    /**
     * 事务管理器
     */
    private final AsyncTransactionManager transactionManager;

    /**
     * 异步任务服务
     */
    private final AsyncTaskService asyncTaskService;

    /**
     * 节点任务仓库
     */
    private final FlowTaskRepository flowTaskRepository;

    /**
     * 节点仓库
     */
    private final TaskNodeRepository taskNodeRepository;

    /**
     * 节点关系仓库
     */
    private final TaskNodeMapRepository taskNodeMapRepository;

    /**
     * 所有执行策略
     */
    private final Map<String, ExecuteStrategy> executeStrategies;

    /**
     * 调度系统，用来调度构建流式任务关系图
     */
    private final SchedulerSystem schedulerSystem;

    /**
     * 定期从数据库捞取一次当前所有流式任务，防止在其他机器添加的流式任务然后其他机器挂了
     */
    private final SchedulerTask flowTaskLoader;

    /**
     * 流式任务引擎，这里主要用于判断任务状态
     */
    private final StreamTaskEngine streamTaskEngine;

    /**
     * 启动器，用于控制状态
     */
    private final Starter starter;

    private final ProcessorRegistry processorRegistry;

    private final TraceService traceService;

    public FlowServiceImpl(FlowServiceConfig config, StreamTaskEngine streamTaskEngine) {
        Const.VALIDATION_SERVICE.validate(config);
        schedulerSystem = config.getSchedulerSystem();
        transactionManager = config.getTransactionManager();
        streamNodeMapBatchSize = config.getStreamNodeMapBatchSize();
        processorRegistry = config.getProcessorRegistry();
        idGenerator = config.getIdGenerator();
        asyncTaskService = config.getAsyncTaskService();
        flowTaskRepository = config.getFlowTaskRepository() == null ? new FlowTaskRepositoryImpl(transactionManager)
            : config.getFlowTaskRepository();
        taskNodeRepository = config.getTaskNodeRepository() == null ? new TaskNodeRepositoryImpl(transactionManager)
            : config.getTaskNodeRepository();
        taskNodeMapRepository = config.getTaskNodeMapRepository() == null
            ? new TaskNodeMapRepositoryImpl(transactionManager) : config.getTaskNodeMapRepository();

        executeStrategies = config.getExecuteStrategies();

        starter = new Starter();
        traceService = config.getTraceService();

        this.streamTaskEngine = streamTaskEngine;

        // 流式任务线程批处理
        flowTaskLoader = new SimpleSchedulerTask(() -> {
            boolean hasData = true;

            int offset = 0;
            int limit = 20;

            // 还有数据，并且当前还没达到用户配置的批处理数量，继续捞取数据
            List<FlowTask> allFlowTasks = new ArrayList<>();
            while (hasData) {
                // 计算本次捞取数量
                List<FlowTask> flowTasks = flowTaskRepository.selectByType(FlowTaskType.STREAM, offset, limit);
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("本次捞取到的流式任务为： [{}]", flowTasks);
                }

                hasData = !CollectionUtil.isEmpty(flowTasks);
                offset += limit;
                allFlowTasks.addAll(flowTasks);

            }

            Set<String> currentTaskIds =
                schedulerSystem.getAll().stream().map(TaskDescriptor::getId).collect(Collectors.toSet());

            // 将捞取到的数据加到调度系统中去
            for (final FlowTask flowTask : allFlowTasks) {
                currentTaskIds.remove(flowTask.getRequestId());
                registerStreamTaskBuildTask(flowTask.getRequestId());
            }

            // 没有移除说明本次没有捞取到指定任务，则需要将其从调度系统移除，因为任务可能已经被关闭了
            for (final String currentTaskId : currentTaskIds) {
                schedulerSystem.removeTask(currentTaskId);
            }
        }, "stream-task-batch-add", true);
        // 5秒兜底调度一次，注意，这个调度越快，系统负载越高，但是当其他系统挂了后那个系统添加的任务可能的停顿时间越短，反之；
        flowTaskLoader.setFixedDelay(1000 * 15);
    }

    @Override
    public void start() {
        starter.start(() -> {
            // 注意，这两个启动顺序不是随便定的
            schedulerSystem.start();
            flowTaskLoader.start();
        });
    }

    @Override
    public void stop() {
        starter.stop(() -> {
            // 注意，这两个关闭顺序不是随便定的
            flowTaskLoader.stop();
            schedulerSystem.stop();
        });
    }

    @Override
    public void addTask(final FlowTaskModel task) {
        Assert.notNull(task, "要添加的任务不能为null", ExceptionProviderConst.IllegalArgumentExceptionProvider);
        starter.runWithStarted(() -> {
            Const.VALIDATION_SERVICE.validate(task);
            if (task instanceof SetTaskModel) {
                addSetTask((SetTaskModel)task);
            } else if (task instanceof StreamTaskModel) {
                addStreamTask((StreamTaskModel)task);
            } else {
                throw new UnsupportedOperationException(StringUtils.format("不支持的task类型： [{}]", task.getClass()));
            }
        });
    }

    @Override
    public void finishStream(final String streamId) {
        flowTaskRepository.updateStatus(streamId, FlowTaskStatus.FINISH);
    }

    @Override
    public FlowTaskStatus queryTaskStatus(final String requestId) {
        Assert.notBlank(requestId, StringUtils.format("任务requestId不能为空"),
            ExceptionProviderConst.IllegalArgumentExceptionProvider);
        return starter.runWithStarted(() -> {
            FlowTask flowTask = flowTaskRepository.selectForLock(requestId);
            return flowTask == null ? null : flowTask.getStatus();
        });
    }

    @Override
    public SetTaskModel getSetTaskModel(String requestId) throws IllegalArgumentException, IllegalStateException {
        Assert.notBlank(requestId, StringUtils.format("任务requestId不能为空"),
            ExceptionProviderConst.IllegalArgumentExceptionProvider);
        return starter.runWithStarted(() -> {
            FlowTask flowTask = flowTaskRepository.selectForLock(requestId);
            if (flowTask == null) {
                return null;
            }

            Assert.assertTrue(flowTask.getType() == FlowTaskType.SET,
                StringUtils.format("当前任务类型不是SetTaskModel: [{}:{}]", requestId, flowTask.getType()),
                ExceptionProviderConst.IllegalArgumentExceptionProvider);

            List<TaskNode> taskNodeList = new ArrayList<>();
            int batchSize = 500;
            int offset = 0;
            while (true) {
                List<TaskNode> list = taskNodeRepository.selectByTaskRequestId(requestId, offset, batchSize);
                taskNodeList.addAll(list);
                if (list.isEmpty() || list.size() < 500) {
                    break;
                }
                offset += 500;
            }

            List<TaskNodeMap> taskNodeMapList = new ArrayList<>();
            offset = 0;
            while (true) {
                List<TaskNodeMap> list = taskNodeMapRepository.selectByTaskRequestId(requestId, offset, batchSize);
                taskNodeMapList.addAll(list);
                if (list.isEmpty() || list.size() < 500) {
                    break;
                }
                offset += 500;
            }

            return convert(flowTask, taskNodeList, taskNodeMapList);
        });
    }

    @Override
    public TaskNodeStatus queryNodeStatus(final String nodeRequestId) {
        Assert.notBlank(nodeRequestId, StringUtils.format("任务节点requestId不能为空"),
            ExceptionProviderConst.IllegalArgumentExceptionProvider);
        return starter.runWithStarted(() -> {
            TaskNode taskNode = taskNodeRepository.selectByRequestId(nodeRequestId);
            return taskNode == null ? null : taskNode.getStatus();
        });
    }

    @Override
    public boolean notifyNode(String requestId, TransStrategy transStrategy) {
        return asyncTaskService.notifyTask(requestId, transStrategy);
    }

    @Override
    public Set<String> notifyNode(Set<String> requestIdSet, TransStrategy transStrategy) {
        return asyncTaskService.notifyTask(requestIdSet, transStrategy);
    }

    /**
     * 将数据库中的任务重新构建为内存模型
     * 
     * @param task
     *            主任务
     * @param taskNodeList
     *            所有节点列表
     * @param taskNodeMapList
     *            节点关系列表
     * @return 有限集任务模型
     */
    private SetTaskModel convert(FlowTask task, List<TaskNode> taskNodeList, List<TaskNodeMap> taskNodeMapList) {
        Map<String, TaskNode> nodeMap =
            taskNodeList.stream().collect(Collectors.toMap(TaskNode::getRequestId, Function.identity()));

        Map<String, TaskNodeModel> taskNodeModelMap = new HashMap<>();
        Map<String, String> parentMap = new HashMap<>();
        for (TaskNodeMap taskNodeMap : taskNodeMapList) {
            parentMap.put(taskNodeMap.getChildNode(), taskNodeMap.getParentNode());
            TaskNode parentTaskNode = nodeMap.get(taskNodeMap.getParentNode());
            TaskNode childTaskNode = nodeMap.get(taskNodeMap.getChildNode());
            TaskNodeModel parentNodeModel =
                taskNodeModelMap.computeIfAbsent(parentTaskNode.getRequestId(), requestId -> convert(parentTaskNode));
            TaskNodeModel childNodeModel =
                taskNodeModelMap.computeIfAbsent(childTaskNode.getRequestId(), requestId -> convert(childTaskNode));
            parentNodeModel.getAllChild().add(childNodeModel);
        }

        TaskNodeModel firstTask = null;
        TaskNodeModel lastTask = null;
        for (TaskNodeModel nodeModel : taskNodeModelMap.values()) {
            if (!parentMap.containsKey(nodeModel.getRequestId())) {
                if (firstTask != null) {
                    throw new RuntimeException(
                        StringUtils.format("当前有两个父节点: [{}], [{}], [{}]", task, nodeModel, firstTask));
                }
                firstTask = nodeModel;
            } else if (nodeModel.getAllChild().isEmpty()) {
                if (lastTask != null) {
                    throw new RuntimeException(
                        StringUtils.format("当前有两个尾节点: [{}], [{}], [{}]", task, nodeModel, lastTask));
                }
                lastTask = nodeModel;
            }
        }

        SetTaskModel taskModel = new SetTaskModel();
        taskModel.setRequestId(task.getRequestId());
        taskModel.setFirstTask(firstTask);
        taskModel.setLastTask(lastTask);
        return taskModel;
    }

    private TaskNodeModel convert(TaskNode taskNode) {
        String processorName = taskNode.getProcessor();
        AbstractAsyncTaskProcessor<?> processor = processorRegistry.getProcessor(processorName);
        TaskNodeModel taskNodeModel = new TaskNodeModel();
        taskNodeModel.setRequestId(taskNode.getRequestId());
        taskNodeModel.setData(processor.deserialize(taskNode.getRequestId(), taskNode.getNodeData(), new HashMap<>()));
        taskNodeModel.setProcessor(processorName);
        taskNodeModel.setFailStrategy(taskNode.getFailStrategy());
        taskNodeModel.setExecuteStrategy(taskNode.getExecuteStrategy());
        taskNodeModel.setMaxRetry(taskNode.getMaxRetry());
        taskNodeModel.setStrategyContext(taskNode.getStrategyContext());
        taskNodeModel.setAllChild(new ArrayList<>());
        return taskNodeModel;
    }

    /**
     * 注册流式任务构建任务
     *
     * @param flowTaskRequestId
     *            要注册的流式任务的requestId
     */
    private void registerStreamTaskBuildTask(String flowTaskRequestId) {
        // 这里可能注册失败，不过我们并不关心
        schedulerSystem.registerTask(new TaskDescriptor(flowTaskRequestId, 1000 * 10, () -> {
            Throwable throwable = null;
            Object trace = null;
            try {
                if (traceService != null) {
                    trace = traceService.newTrace();
                }
                long start = System.currentTimeMillis();
                buildNodeMap(flowTaskRequestId);
                LOGGER.debug("无限流任务构建完毕,  [{}:{}]", flowTaskRequestId, (System.currentTimeMillis() - start));
            } catch (Throwable e) {
                throwable = e;
                Throwable rootCause = ExceptionUtil.getRootCause(e);
                // 如果是锁定异常，应该忽略
                if (!(rootCause instanceof SQLException)
                    || !SqlUtil.causeForUpdateNowaitError((SQLException)rootCause)) {
                    // 如果这里因为网络抖动等导致异常，应该可以快速重试的，现在暂时没有处理
                    LOGGER.warn(e, "流式任务批量添加处理失败，等待下次处理");
                }
            } finally {
                if (traceService != null) {
                    try {
                        traceService.finish(trace, false, null, throwable);
                    } catch (Throwable e1) {
                        LOGGER.warn(e1, "trace结束异常");
                    }
                }
            }
        }, true));
    }

    /**
     * 从数据库捞取指定流式任务的INIT状态的子任务，将其构建为一个单链附加到当前主任务的执行链的末尾；同时再构建完执行链后判断是否需要主动唤醒（有可能原链执行完了，需要从新链起始位置执行，此时需要主动唤醒）
     *
     * @param taskRequestId
     *            流式任务主任务的requestId
     * @return 是否还有更多数据，如果返回true，表示还有数据未处理，需要下次继续处理
     */
    private boolean buildNodeMap(String taskRequestId) {
        // 单链构建完毕，准备更新到数据库
        AtomicBoolean hasMoreData = new AtomicBoolean(true);

        transactionManager.runWithTrans(TransStrategy.REQUIRED, () -> {
            /*
             * 1、锁定主任务
             */
            FlowTask flowTask = flowTaskRepository.selectForLock(taskRequestId);
            Assert.notNull(flowTask, StringUtils.format("系统错误，当前主任务 [{}] 不存在", taskRequestId),
                ExceptionProviderConst.DBExceptionProvider);

            /*
             * 2、捞取INIT状态的子任务节点，将其构建为一条单链附加到当前主任务的任务链的最后；
             *
             * PS: 注意，锁定主任务后再开始捞取子任务，否则可能我们捞取完子任务后子任务状态被其他进程修改了；
             */
            int offset = 0;
            int limit = 100;
            // 剩余批处理数量
            int streamNodeMapBatchSize = this.streamNodeMapBatchSize;
            List<TaskNode> taskNodes = new ArrayList<>();
            while (streamNodeMapBatchSize > 0) {
                // 本次加载数据数量
                int load = Math.min(limit, streamNodeMapBatchSize);
                List<TaskNode> nodes =
                    taskNodeRepository.selectByStatus(taskRequestId, TaskNodeStatus.INIT, offset, load);
                taskNodes.addAll(nodes);
                streamNodeMapBatchSize -= load;
                offset += load;

                if (nodes.size() < load) {
                    hasMoreData.set(false);
                    break;
                } else {
                    hasMoreData.set(true);
                }
            }

            // 没有数据直接返回
            if (taskNodes.isEmpty()) {
                return;
            }

            // 开始处理，将所有node构建为一个单链
            List<TaskNodeMap> taskNodeMaps = new ArrayList<>(taskNodes.size());
            TaskNode lastNode = taskNodes.get(0);
            for (int i = 1; i < taskNodes.size(); i++) {
                TaskNode node = taskNodes.get(i);
                TaskNodeMap map = new TaskNodeMap();
                map.setId(idGenerator.generateId());
                map.setTaskRequestId(taskRequestId);
                map.setParentNode(lastNode.getRequestId());
                map.setChildNode(node.getRequestId());
                taskNodeMaps.add(map);
                lastNode = node;
            }

            String oldLastNodeRequestId = flowTask.getLastTaskId();

            TaskNode newFirstNode = taskNodes.get(0);
            TaskNode newLastNode = taskNodes.get(taskNodes.size() - 1);

            TaskNodeMap map = new TaskNodeMap();
            map.setId(idGenerator.generateId());
            map.setTaskRequestId(taskRequestId);
            map.setParentNode(oldLastNodeRequestId);
            map.setChildNode(newFirstNode.getRequestId());
            taskNodeMaps.add(map);

            /*
             * 3、开始决策我们新加的任务链的第一个任务是否应该被立即调起
             */
            TaskNode oldLastNode = taskNodeRepository.selectForUpdate(oldLastNodeRequestId);
            TaskNodeStatus oldLastNodeStatus = oldLastNode.getStatus();

            // 第一个节点的状态，默认是WAIT
            TaskNodeStatus firstNodeStatus = TaskNodeStatus.WAIT;
            // 如果当前最后一个节点已经执行完成（除去pending状态），那么他就没有机会调度我们新增节点了，此时应该我们自己调度，但是如果最后一个节点
            // 还没完成，那么他肯定有机会调度我们的新增节点（因为我们加的有锁，最后一个节点获取新节点时也会加锁），我们就无需在这里调度了；
            if (oldLastNodeStatus == TaskNodeStatus.SUCCESS) {
                // 节点已经执行成功了，此时我们修改第一个节点为READY，并在稍后直接调度
                firstNodeStatus = TaskNodeStatus.READY;
            } else if (oldLastNodeStatus == TaskNodeStatus.ERROR) {
                // 最后一个节点执行失败了，此时我们动态决策新的第一个节点应该是什么状态
                StrategyResult result = streamTaskEngine.decideNodeStatus(null, newFirstNode);
                // 注意，无论最终结果是PENDING还是RUNNING，节点都应该在稍后被立即调度起来
                switch (result) {
                    case PENDING:
                        firstNodeStatus = TaskNodeStatus.PENDING;
                        break;
                    case RUNNING:
                        firstNodeStatus = TaskNodeStatus.READY;
                        break;
                    default:
                        throw new IllegalStateException(
                            StringUtils.format("流式任务节点 [{}] 决策失败，结果不应该式UNKNOWN", newFirstNode.getRequestId()));
                }
            }

            List<String> taskNodeRequestIds = taskNodes.stream().map(TaskNode::getRequestId)
                .filter(str -> !str.equals(newFirstNode.getRequestId())).collect(Collectors.toList());
            /*
             * 4、开始更新数据库
             */
            // 保存任务链
            taskNodeMapRepository.save(taskNodeMaps);
            // 修改任务状态
            taskNodeRepository.batchUpdateStatus(taskNodeRequestIds, TaskNodeStatus.WAIT);
            // 更新第一个任务状态
            taskNodeRepository.updateStatus(newFirstNode.getRequestId(), firstNodeStatus);
            // 更新主任务的最后一个任务requestId
            flowTaskRepository.updateLastTaskId(taskRequestId, newLastNode.getRequestId());

            /*
             * 5、最后，如果任务状态不是WAIT，那么通知执行
             */
            if (firstNodeStatus != TaskNodeStatus.WAIT) {
                asyncTaskService.notifyTask(newFirstNode.getRequestId());
            }
        });

        return hasMoreData.get();
    }

    /**
     * 添加流式任务
     * 
     * @param taskModel
     *            任务模型
     */
    private void addStreamTask(StreamTaskModel taskModel) {

        TaskNodeModel firstTask = taskModel.getFirstTask();

        String streamId = taskModel.getStreamId();
        FlowTask task = new FlowTask();
        task.setId(streamId);
        task.setRequestId(streamId);
        task.setType(FlowTaskType.STREAM);
        task.setFirstTaskId(firstTask.getRequestId());
        task.setStatus(FlowTaskStatus.RUNNING);

        Pair<List<TaskNode>, List<TaskNodeMap>> pair = buildGraph(firstTask, 1, streamId, null,
            new Pair<>(new ArrayList<>(), new ArrayList<>()), new HashSet<>(), new HashSet<>());
        List<TaskNode> nodes = pair.getKey();

        task.setLastTaskId(nodes.get(nodes.size() - 1).getRequestId());

        transactionManager.runWithTrans(TransStrategy.REQUIRED, () -> {
            /*
             * 1、如果主任务已经存在，那么锁定，否则创建
             */
            boolean createFlow = false;
            FlowTask taskFromDB = flowTaskRepository.selectForLock(streamId);
            if (taskFromDB == null) {
                try {
                    flowTaskRepository.save(task);
                    createFlow = true;
                } catch (RuntimeException throwable) {
                    // 如果是主键冲突，可能是并发了，此时应该忽略异常
                    Throwable rootCause = ExceptionUtil.getRootCause(throwable);

                    if (!(rootCause instanceof SQLException) || !SqlUtil.causeDuplicateKey((SQLException)rootCause)) {
                        throw throwable;
                    }

                    // 到这里表明是因为主键冲突导致的异常，肯定是并发创建主任务了，此时肯定是存在主任务了，我们只需要加锁即可；
                    taskFromDB = flowTaskRepository.selectForLock(streamId);
                    Assert.notNull(taskFromDB, StringUtils.format("程序未知BUG，当前任务[{}]不存在", streamId),
                        ExceptionProviderConst.IllegalStateExceptionProvider);
                }
            }

            // 默认node构建完是WAIT状态，如果当前没有创建主任务，也就是主任务已经存在了，那么新建任务应该是init状态
            /*
             * 2、开始保存子任务、创建异步任务；此时分两种情况
             * - 当前主任务已经存在：此时新建任务应该是init状态，唤醒节点关系构建线程来构建节点关系而不是使用我们构建的节点关系（稍后会有专门的线程做这个事情）；
             * - 当前主任务不存在：此时新建任务应该是wait状态，并且第一个任务应该立即唤醒，需要我们保存我们的节点关系；
             */
            if (createFlow) {
                // 有可能第一次添加仅仅添加了一个节点，所以此时没有node map，也不应该保存
                if (!pair.getValue().isEmpty()) {
                    // 创建了主任务，那么也应该把任务节点关系构建出来
                    taskNodeMapRepository.save(pair.getValue());
                }
            } else {
                // 没有创建主任务，节点状态应该是init
                for (final TaskNode node : nodes) {
                    node.setStatus(TaskNodeStatus.INIT);
                }
            }

            // 保存任务节点
            taskNodeRepository.save(nodes);

            for (final TaskNode node : nodes) {
                asyncTaskService.addTaskWithWait(node.getRequestId(), node.getRequestId(), node.getMaxRetry(),
                    LocalDateTime.now(), StreamTaskEngine.PROCESSOR_NAME, TransStrategy.SUPPORTS);
            }

            if (createFlow) {
                taskNodeRepository.updateStatus(firstTask.getRequestId(), TaskNodeStatus.READY);
                asyncTaskService.notifyTask(firstTask.getRequestId());
            }
        });

        transactionManager.runAfterCommit(() -> {
            registerStreamTaskBuildTask(streamId);
            schedulerSystem.scheduler(streamId, false);
        });
    }

    /**
     * 添加有限集任务
     * 
     * @param taskModel
     *            任务
     */
    private void addSetTask(SetTaskModel taskModel) {
        String flowRequestId = taskModel.getRequestId();
        // 先构建主任务
        FlowTask flowTask = new FlowTask();
        flowTask.setId(idGenerator.generateId());
        flowTask.setRequestId(flowRequestId);
        flowTask.setType(FlowTaskType.SET);
        flowTask.setFirstTaskId(taskModel.getFirstTask().getRequestId());
        flowTask.setLastTaskId(taskModel.getLastTask().getRequestId());
        flowTask.setStatus(FlowTaskStatus.RUNNING);

        // 构建节点关系图
        Pair<List<TaskNode>, List<TaskNodeMap>> pair =
            buildGraph(taskModel.getFirstTask(), 0, flowRequestId, taskModel.getLastTask(),
                new Pair<>(new ArrayList<>(), new ArrayList<>()), new HashSet<>(), new HashSet<>());

        // 开启事务
        transactionManager.runWithTrans(TransStrategy.REQUIRED, () -> {
            flowTaskRepository.save(flowTask);
            // 将第一个节点直接设置为ready状态，这里虽然是遍历，但是一般第一个就是第一个节点，这里只是做了兜底，防止后续开发过程中不小心更改了顺序，导致list中的第一个不是真正的第一个执行节点
            for (final TaskNode taskNode : pair.getKey()) {
                if (taskNode.getRequestId().equals(taskModel.getFirstTask().getRequestId())) {
                    taskNode.setStatus(TaskNodeStatus.READY);
                }
            }

            taskNodeRepository.save(pair.getKey());
            taskNodeMapRepository.save(pair.getValue());
            for (final TaskNode node : pair.getKey()) {
                asyncTaskService.addTaskWithWait(node.getRequestId(), node.getRequestId(), node.getMaxRetry(),
                    LocalDateTime.now(), SetTaskEngine.PROCESSOR_NAME, TransStrategy.SUPPORTS);
            }

        });

        // 注意，这个一定要放在事务执行后再执行，否则会有时序问题
        asyncTaskService.notifyTask(flowTask.getFirstTaskId());
    }

    /**
     * 从根节点开始深度优先构建有向无环图
     * 
     * @param model
     *            根节点
     * @param maxChildSize
     *            节点的最大子节点数量，小于等于0表示不限
     * @param flowTaskRequestId
     *            主任务requestId
     * @param lastNode
     *            最后一个节点，允许为空，为空时不对最后一个节点进行校验
     * @param pair
     *            当前图集合
     * @param existNode
     *            用于循环检测的冗余集合
     * @param allNodes
     *            所有添加过的节点，防止重复添加；PS：如果没有这个校验，在一个节点有多个父/子节点的时候可能会出错；
     * @return 构建好的有向无环图集合
     */
    private Pair<List<TaskNode>, List<TaskNodeMap>> buildGraph(TaskNodeModel model, int maxChildSize,
        String flowTaskRequestId, TaskNodeModel lastNode, Pair<List<TaskNode>, List<TaskNodeMap>> pair,
        Set<String> existNode, Set<String> allNodes) {
        Assert.assertTrue(existNode.add(model.getRequestId()),
            StringUtils.format("当前在依赖 [{}] 处存在环形依赖，请检测， [{}]", model.getRequestId(), existNode),
            ExceptionProviderConst.IllegalArgumentExceptionProvider);

        if (allNodes.add(model.getRequestId())) {
            // 添加该节点到结果集中
            pair.getKey().add(build(model, idGenerator, flowTaskRequestId));
        }

        List<TaskNodeModel> allChild = model.getAllChild();

        if (CollectionUtil.isEmpty(allChild)) {
            // 如果当前节点没有子节点，那么说明当前节点是最后一个节点，做一些基础校验
            if (lastNode != null) {
                Assert.assertTrue(
                    lastNode == model, StringUtils.format("当前任务[{}]的结束节点[{}]存在多个对象", flowTaskRequestId,
                        model.getRequestId(), lastNode.getRequestId()),
                    ExceptionProviderConst.IllegalArgumentExceptionProvider);
            }
        } else {
            if (maxChildSize > 0) {
                Assert.assertTrue(
                    allChild.size() <= maxChildSize, StringUtils.format("当前允许的最大子节点数为： [{}]， 当前节点 [{}] 有 [{}] 个子节点",
                        maxChildSize, model.getRequestId(), allChild.size()),
                    ExceptionProviderConst.IllegalArgumentExceptionProvider);
            }

            for (final TaskNodeModel child : allChild) {
                // 构建节点关系存储到图中
                TaskNodeMap map = new TaskNodeMap();
                map.setId(idGenerator.generateId());
                map.setTaskRequestId(flowTaskRequestId);
                map.setParentNode(model.getRequestId());
                map.setChildNode(child.getRequestId());
                pair.getValue().add(map);

                buildGraph(child, maxChildSize, flowTaskRequestId, lastNode, pair, existNode, allNodes);
            }
        }

        // 构建完该节点的子节点后将本节点从已知节点删除，否则如果某个节点有两个父节点时环形依赖检测会出问题；
        // 例如A依赖B、C，B依赖D，C也依赖D，如果没有这个删除操作，这个正常依赖将被检测出环
        existNode.remove(model.getRequestId());
        return pair;
    }

    /**
     * 根据node模型构建存储node
     * 
     * @param model
     *            node模型
     * @param idGenerator
     *            id生成器
     * @param flowTaskRequestId
     *            node对应的主任务的requestId
     * @return 存储的node
     */
    private TaskNode build(TaskNodeModel model, IDGenerator idGenerator, String flowTaskRequestId) {
        String processorName =
            StringUtils.getOrDefault(model.getProcessor(), model.getData().getClass().getSimpleName());
        AbstractAsyncTaskProcessor<?> processor = processorRegistry.getProcessor(processorName);
        Assert.notNull(processor,
            StringUtils.format("任务 [{}:{}] 对应的处理器 [{}] 不存在", flowTaskRequestId, model.getRequestId(), processorName),
            ExceptionProviderConst.IllegalArgumentExceptionProvider);
        ExecuteStrategy executeStrategy = executeStrategies.get(model.getExecuteStrategy());
        Assert.notNull(executeStrategy, StringUtils.format("任务 [{}:{}] 对应的执行策略 [{}] 不存在", flowTaskRequestId,
            model.getRequestId(), model.getExecuteStrategy()), ExceptionProviderConst.IllegalArgumentExceptionProvider);

        TaskNode node = new TaskNode();
        node.setId(idGenerator.generateId());
        node.setRequestId(model.getRequestId());
        node.setTaskRequestId(flowTaskRequestId);
        node.setNodeData(processor.serialize(model.getData()));
        node.setProcessor(processorName);
        node.setStatus(TaskNodeStatus.WAIT);
        node.setFailStrategy(model.getFailStrategy());
        node.setExecuteStrategy(model.getExecuteStrategy());
        node.setStrategyContext(StringUtils.getOrDefault(model.getStrategyContext(), Const.NULL));
        node.setMaxRetry(model.getMaxRetry());
        return node;
    }
}
