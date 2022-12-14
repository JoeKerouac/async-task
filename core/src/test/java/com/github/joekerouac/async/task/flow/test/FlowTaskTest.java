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

import com.github.joekerouac.common.tools.thread.ThreadUtil;
import com.github.joekerouac.async.task.flow.AbstractFlowProcessor;
import com.github.joekerouac.async.task.flow.FlowService;
import com.github.joekerouac.async.task.flow.enums.FailStrategy;
import com.github.joekerouac.async.task.flow.impl.StrategyConst;
import com.github.joekerouac.async.task.flow.model.FlowServiceConfig;
import com.github.joekerouac.async.task.flow.model.SetTaskModel;
import com.github.joekerouac.async.task.flow.model.StreamTaskModel;
import com.github.joekerouac.async.task.flow.model.TaskNodeModel;
import com.github.joekerouac.async.task.flow.service.FlowServiceImpl;
import com.github.joekerouac.async.task.flow.spi.FlowMonitorService;
import com.github.joekerouac.async.task.impl.SimpleConnectionSelector;
import com.github.joekerouac.async.task.model.ExecResult;
import com.github.joekerouac.async.task.test.TestEngine;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ??????????????????????????????????????????processor??????????????????????????????????????????????????????????????????
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
        FlowServiceConfig config = new FlowServiceConfig();
        config.setIdGenerator(asyncServiceConfig.getIdGenerator());
        config.setConnectionSelector(new SimpleConnectionSelector(dataSource));
        config.setTransactionHook(asyncServiceConfig.getTransactionHook());
        config.setAsyncTaskService(asyncTaskService);
        config.setFlowMonitorService(new FlowMonitorService() {});
        flowService = new FlowServiceImpl(config);
        flowService.start();
    }

    @AfterSuite
    @Override
    public void destroy() {
        flowService.stop();
        super.destroy();
    }

    /**
     * SetTask????????????????????????????????????????????????????????????????????????????????????????????????
     *
     * @throws Exception
     *             ??????
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
        flowService.addProcessor(processor);

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

            // ??????????????????
            for (int i = 0; i < processor.contexts.size(); i++) {
                Assert.assertEquals(processor.contexts.get(i).id, i + 1);
            }
        } finally {
            for (final String name : processor.processors()) {
                Assert.assertNotNull(flowService.removeProcessor(name));
            }
        }
    }

    /**
     * stream task?????????????????????4????????????????????????????????????????????????????????????????????????????????????????????????????????????
     *
     * @throws Exception
     *             ??????
     */
    @Test
    public void streamTaskBaseTest() throws Exception {
        // ????????????ID
        String streamId = UUID.randomUUID().toString();
        String processorName = "streamTaskBaseTest";

        // ???????????????????????????
        {
            CountDownLatch latch = new CountDownLatch(2);
            TestTaskFlowProcessor processor = new TestTaskFlowProcessor(latch) {
                @Override
                public String[] processors() {
                    return new String[] {processorName};
                }
            };

            flowService.addProcessor(processor);

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

                // ??????????????????
                for (int i = 0; i < processor.contexts.size(); i++) {
                    Assert.assertEquals(processor.contexts.get(i).id, i + 1);
                }
            } finally {
                for (final String name : processor.processors()) {
                    Assert.assertNotNull(flowService.removeProcessor(name));
                }
            }
        }

        // ?????????????????????????????????
        {
            CountDownLatch latch = new CountDownLatch(2);
            TestTaskFlowProcessor processor = new TestTaskFlowProcessor(latch) {
                @Override
                public String[] processors() {
                    return new String[] {processorName};
                }
            };
            flowService.addProcessor(processor);

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

                // ??????????????????
                for (int i = 0; i < processor.contexts.size(); i++) {
                    Assert.assertEquals(processor.contexts.get(i).id, i + 3);
                }
            } finally {
                for (final String name : processor.processors()) {
                    Assert.assertNotNull(flowService.removeProcessor(name));
                }
            }
        }
    }

    /**
     * SetTask????????????1???????????????1???2???3???4?????????????????????????????????1???1????????????????????????2???3???????????????2???3????????????????????????4????????????4????????????????????????
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
        flowService.addProcessor(processor);

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

            // ??????????????????
            Assert.assertTrue(latch.await(3, TimeUnit.SECONDS));
            Assert.assertEquals(processor.contexts.size(), 4);

            Assert.assertEquals(processor.contexts.get(0).id, 1);
            // ??????2???3????????????????????????????????????????????????????????????
            Assert.assertTrue(processor.contexts.get(1).id == 2 || processor.contexts.get(1).id == 3);
            Assert.assertTrue(processor.contexts.get(2).id == 2 || processor.contexts.get(2).id == 3);
            // ??????????????????????????????????????????
            Assert.assertEquals(processor.contexts.get(3).id, 4);
        } finally {
            for (final String name : processor.processors()) {
                Assert.assertNotNull(flowService.removeProcessor(name));
            }
        }
    }

    /**
     * ?????????????????????????????????
     *
     * @param context
     *            name
     * @param id
     *            age
     * @param processor
     *            processor
     * @return ????????????
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

    private static class TestTaskFlowProcessor extends AbstractFlowProcessor<TestTask> {

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
            System.out.println("?????????????????????" + context);
            if (consumer != null) {
                consumer.accept(context);
            }

            // ??????sleep??????????????????????????????????????????????????????????????????????????????????????????
            ThreadUtil.sleep(new Random().nextInt() % 50 + 10, TimeUnit.MILLISECONDS);
            contexts.add(context);
            latch.countDown();
            return ExecResult.SUCCESS;
        }
    }

}
