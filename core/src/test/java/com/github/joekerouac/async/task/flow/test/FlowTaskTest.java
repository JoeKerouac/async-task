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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.testng.Assert;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import com.github.joekerouac.async.task.flow.FlowService;
import com.github.joekerouac.async.task.flow.enums.FailStrategy;
import com.github.joekerouac.async.task.flow.impl.StrategyConst;
import com.github.joekerouac.async.task.flow.impl.repository.FlowTaskRepositoryImpl;
import com.github.joekerouac.async.task.flow.impl.repository.TaskNodeMapRepositoryImpl;
import com.github.joekerouac.async.task.flow.impl.repository.TaskNodeRepositoryImpl;
import com.github.joekerouac.async.task.flow.model.FlowServiceConfig;
import com.github.joekerouac.async.task.flow.model.SetTaskModel;
import com.github.joekerouac.async.task.flow.model.StreamTaskModel;
import com.github.joekerouac.async.task.flow.model.TaskNodeModel;
import com.github.joekerouac.async.task.flow.service.AbstractFlowTaskEngine;
import com.github.joekerouac.async.task.flow.service.FlowServiceImpl;
import com.github.joekerouac.async.task.flow.service.SetTaskEngine;
import com.github.joekerouac.async.task.flow.service.StreamTaskEngine;
import com.github.joekerouac.async.task.flow.spi.FlowMonitorService;
import com.github.joekerouac.async.task.model.ExecResult;
import com.github.joekerouac.async.task.spi.AbstractAsyncTaskProcessor;
import com.github.joekerouac.async.task.test.TestEngine;
import com.github.joekerouac.common.tools.scheduler.SchedulerSystemImpl;
import com.github.joekerouac.common.tools.thread.ThreadPoolConfig;
import com.github.joekerouac.common.tools.thread.ThreadUtil;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 因为这个测试用例使用了同一个processor，所以多个测试用例间不能并行测试，只能串行；
 * 
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public class FlowTaskTest extends TestEngine {

    protected FlowService flowService;

    @BeforeSuite
    @Override
    public void init() throws Exception {
        super.init();
        SchedulerSystemImpl schedulerSystem =
            new SchedulerSystemImpl("流式任务调度系统", ThreadUtil.newThreadPool(new ThreadPoolConfig()), true);

        FlowServiceConfig flowServiceConfig = new FlowServiceConfig();
        flowServiceConfig.setIdGenerator(asyncServiceConfig.getIdGenerator());
        flowServiceConfig.setProcessorRegistry(processorRegistry);
        flowServiceConfig.setAsyncTaskService(asyncTaskService);
        flowServiceConfig.setFlowMonitorService(new FlowMonitorService() {});
        flowServiceConfig.setFlowTaskRepository(new FlowTaskRepositoryImpl(transactionManager));
        flowServiceConfig.setTaskNodeRepository(new TaskNodeRepositoryImpl(transactionManager));
        flowServiceConfig.setTaskNodeMapRepository(new TaskNodeMapRepositoryImpl(transactionManager));
        flowServiceConfig.setTransactionManager(transactionManager);
        flowServiceConfig.setSchedulerSystem(schedulerSystem);

        AbstractFlowTaskEngine.EngineConfig engineConfig =
            AbstractFlowTaskEngine.EngineConfig.builder().processorRegistry(flowServiceConfig.getProcessorRegistry())
                .asyncTaskService(flowServiceConfig.getAsyncTaskService())
                .flowMonitorService(flowServiceConfig.getFlowMonitorService())
                .flowTaskRepository(flowServiceConfig.getFlowTaskRepository())
                .taskNodeRepository(flowServiceConfig.getTaskNodeRepository())
                .taskNodeMapRepository(flowServiceConfig.getTaskNodeMapRepository())
                .executeStrategies(flowServiceConfig.getExecuteStrategies())
                .transactionManager(flowServiceConfig.getTransactionManager()).build();

        StreamTaskEngine streamTaskEngine = new StreamTaskEngine(engineConfig, flowServiceConfig.getSchedulerSystem());
        SetTaskEngine setTaskEngine = new SetTaskEngine(engineConfig);
        asyncTaskService.addProcessor(streamTaskEngine);
        asyncTaskService.addProcessor(setTaskEngine);

        flowService = new FlowServiceImpl(flowServiceConfig, streamTaskEngine);
        flowService.start();
    }

    @AfterSuite
    @Override
    public void destroy() {
        flowService.stop();
        super.destroy();
    }

    /**
     * SetTask普通测试，总共两个任务，两个任务串行执行，并且都执行成功的场景；
     *
     * @throws Exception
     *             异常
     */
    @Test
    public void setTaskBaseTest() throws Exception {
        String processorName = "setTaskBaseTest";
        CountDownLatch latch = new CountDownLatch(2);
        TestTaskFlowProcessor processor = new TestTaskFlowProcessor(latch) {
            @Override
            public String[] processors() {
                return new String[] {processorName};
            }
        };
        asyncTaskService.addProcessor(processor);

        try {
            SetTaskModel model = new SetTaskModel();
            model.setRequestId(UUID.randomUUID().toString());
            TaskNodeModel nodeModel0 = buildTest("JoeKerouac1", 1, processorName);
            TaskNodeModel nodeModel1 = buildTest("JoeKerouac2", 2, processorName);
            nodeModel0.setAllChild(Collections.singletonList(nodeModel1));
            model.setFirstTask(nodeModel0);
            model.setLastTask(nodeModel1);
            flowService.addTask(model);

            Assert.assertTrue(latch.await(3, TimeUnit.SECONDS));
            Assert.assertEquals(processor.contexts.size(), 2);

            // 校验执行顺序
            for (int i = 0; i < processor.contexts.size(); i++) {
                Assert.assertEquals(processor.contexts.get(i).id, i + 1);
            }
        } finally {
            for (final String name : processor.processors()) {
                Assert.assertNotNull(asyncTaskService.removeProcessor(name));
            }
        }
    }

    /**
     * stream task基本测试，总共4个任务，先执行两个任务，然后再添加两个任务并执行，并且都执行成功的场景；
     *
     * @throws Exception
     *             异常
     */
    @Test
    public void streamTaskBaseTest() throws Exception {
        // 全局唯一ID
        String streamId = UUID.randomUUID().toString();
        String processorName = "streamTaskBaseTest";

        // 先添加两个任务执行
        {
            CountDownLatch latch = new CountDownLatch(2);
            TestTaskFlowProcessor processor = new TestTaskFlowProcessor(latch) {
                @Override
                public String[] processors() {
                    return new String[] {processorName};
                }
            };

            asyncTaskService.addProcessor(processor);

            try {
                StreamTaskModel model = new StreamTaskModel();
                model.setStreamId(streamId);
                TaskNodeModel nodeModel0 = buildTest("JoeKerouac1", 1, processorName);
                TaskNodeModel nodeModel1 = buildTest("JoeKerouac2", 2, processorName);
                nodeModel0.setAllChild(Collections.singletonList(nodeModel1));
                model.setFirstTask(nodeModel0);
                flowService.addTask(model);

                Assert.assertTrue(latch.await(3, TimeUnit.SECONDS));
                Assert.assertEquals(processor.contexts.size(), 2);

                // 校验执行顺序
                for (int i = 0; i < processor.contexts.size(); i++) {
                    Assert.assertEquals(processor.contexts.get(i).id, i + 1);
                }
            } finally {
                for (final String name : processor.processors()) {
                    Assert.assertNotNull(asyncTaskService.removeProcessor(name));
                }
            }
        }

        // 往流上添加两个任务执行
        {
            CountDownLatch latch = new CountDownLatch(2);
            TestTaskFlowProcessor processor = new TestTaskFlowProcessor(latch) {
                @Override
                public String[] processors() {
                    return new String[] {processorName};
                }
            };
            asyncTaskService.addProcessor(processor);

            try {
                StreamTaskModel model = new StreamTaskModel();
                model.setStreamId(streamId);

                TaskNodeModel nodeModel2 = buildTest("JoeKerouac1", 3, processorName);
                TaskNodeModel nodeModel3 = buildTest("JoeKerouac2", 4, processorName);
                nodeModel2.setAllChild(Collections.singletonList(nodeModel3));
                model.setFirstTask(nodeModel2);
                flowService.addTask(model);

                Assert.assertTrue(latch.await(3, TimeUnit.SECONDS));
                Assert.assertEquals(processor.contexts.size(), 2);

                // 校验执行顺序
                for (int i = 0; i < processor.contexts.size(); i++) {
                    Assert.assertEquals(processor.contexts.get(i).id, i + 3);
                }
            } finally {
                for (final String name : processor.processors()) {
                    Assert.assertNotNull(asyncTaskService.removeProcessor(name));
                }
            }
        }
    }

    /**
     * SetTask复杂场景1测试：任务1、2、3、4，其中第一个任务节点为1，1有两个子任务节点2、3，任务节点2、3有一个共同子任务4（也就是4有两个父任务）；
     *
     * @throws Exception
     *             Exception
     */
    @Test
    public void complexTest1() throws Exception {
        CountDownLatch latch = new CountDownLatch(4);
        String processorName = "complexTest1";

        TestTaskFlowProcessor processor = new TestTaskFlowProcessor(latch) {
            @Override
            public String[] processors() {
                return new String[] {processorName};
            }
        };
        asyncTaskService.addProcessor(processor);

        try {
            SetTaskModel model = new SetTaskModel();
            model.setRequestId(UUID.randomUUID().toString());
            TaskNodeModel nodeModel0 = buildTest("JoeKerouac1", 1, processorName);
            TaskNodeModel nodeModel1 = buildTest("JoeKerouac2", 2, processorName);
            TaskNodeModel nodeModel2 = buildTest("JoeKerouac3", 3, processorName);
            TaskNodeModel nodeModel3 = buildTest("JoeKerouac4", 4, processorName);

            nodeModel0.setAllChild(Arrays.asList(nodeModel1, nodeModel2));

            nodeModel1.setAllChild(Collections.singletonList(nodeModel3));
            nodeModel2.setAllChild(Collections.singletonList(nodeModel3));

            model.setFirstTask(nodeModel0);
            model.setLastTask(nodeModel3);
            flowService.addTask(model);

            // 等待执行完毕
            Assert.assertTrue(latch.await(3, TimeUnit.SECONDS));
            Assert.assertEquals(processor.contexts.size(), 4);

            Assert.assertEquals(processor.contexts.get(0).id, 1);
            // 任务2、3因为是并行执行的，所以谁都可能先执行完毕
            Assert.assertTrue(processor.contexts.get(1).id == 2 || processor.contexts.get(1).id == 3);
            Assert.assertTrue(processor.contexts.get(2).id == 2 || processor.contexts.get(2).id == 3);
            // 最后一个任务肯定最后一个执行
            Assert.assertEquals(processor.contexts.get(3).id, 4);
        } finally {
            for (final String name : processor.processors()) {
                Assert.assertNotNull(asyncTaskService.removeProcessor(name));
            }
        }
    }

    /**
     * 构建一个随机的任务节点
     *
     * @param context
     *            name
     * @param id
     *            age
     * @param processor
     *            processor
     * @return 任务节点
     */
    private TaskNodeModel buildTest(String context, int id, String processor) {
        TaskNodeModel model = new TaskNodeModel();
        model.setRequestId(UUID.randomUUID().toString());
        model.setData(new TestTask(context, id));
        model.setFailStrategy(FailStrategy.IGNORE);
        model.setExecuteStrategy(StrategyConst.ALL_PARENT_FINISH);
        model.setProcessor(processor);
        return model;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class TestTask {
        private String context;
        private int id;
    }

    private static class TestTaskFlowProcessor extends AbstractAsyncTaskProcessor<TestTask> {

        private final CountDownLatch latch;

        private final List<TestTask> contexts;

        private final Consumer<TestTask> consumer;

        public TestTaskFlowProcessor(final CountDownLatch latch) {
            this(latch, null);
        }

        public TestTaskFlowProcessor(final CountDownLatch latch, final Consumer<TestTask> consumer) {
            this.latch = latch;
            this.contexts = new ArrayList<>();
            this.consumer = consumer;
        }

        @Override
        public ExecResult process(final String requestId, final TestTask context, final Map<String, Object> cache)
            throws Throwable {
            System.out.println("准备执行任务：" + context);
            if (consumer != null) {
                consumer.accept(context);
            }

            // 随机sleep一会儿来模拟实际的业务处理，也为我们的并发测试提供更多可能性
            ThreadUtil.sleep(new Random().nextInt() % 50 + 10, TimeUnit.MILLISECONDS);
            contexts.add(context);
            latch.countDown();
            return ExecResult.SUCCESS;
        }
    }

}
