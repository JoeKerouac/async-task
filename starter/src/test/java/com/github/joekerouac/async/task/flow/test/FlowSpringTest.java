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

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.github.joekerouac.async.task.flow.FlowService;
import com.github.joekerouac.async.task.flow.enums.FailStrategy;
import com.github.joekerouac.async.task.flow.impl.StrategyConst;
import com.github.joekerouac.async.task.flow.model.SetTaskModel;
import com.github.joekerouac.async.task.flow.model.TaskNodeModel;
import com.github.joekerouac.async.task.starter.flow.annotations.EnableFlowTask;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
@EnableFlowTask
@SpringBootTest(classes = FlowSpringTest.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@SpringBootApplication
public class FlowSpringTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private FlowService flowService;

    @Autowired
    private SpringStreamTaskProcessor processor;

    /**
     * SetTask普通测试，总共两个任务，两个任务串行执行，并且都执行成功的场景；
     *
     * @throws Exception
     *             异常
     */
    @Test
    public void setTaskBaseTest() throws Exception {
        {
            // 第一个任务执行失败时忽略，并且第二个任务在第一个任务执行结束后不关心结果，此时可以正常执行第二个任务
            processor.setLatch(new CountDownLatch(2));
            processor.contexts.clear();
            SetTaskModel model = new SetTaskModel();
            model.setRequestId(UUID.randomUUID().toString());
            TaskNodeModel nodeModel0 =
                buildTest("JoeKerouac1", 1, FailStrategy.IGNORE, StrategyConst.ALL_PARENT_FINISH, null);
            TaskNodeModel nodeModel1 =
                buildTest("JoeKerouac2", 2, FailStrategy.PENDING, StrategyConst.ALL_PARENT_FINISH, null);

            nodeModel0.setAllChild(Collections.singletonList(nodeModel1));
            model.setFirstTask(nodeModel0);
            model.setLastTask(nodeModel1);
            flowService.addTask(model);

            Assert.assertTrue(processor.latch.await(3, TimeUnit.SECONDS));
            Assert.assertEquals(processor.contexts.size(), 2);

            // 校验执行顺序
            for (int i = 0; i < processor.contexts.size(); i++) {
                Assert.assertEquals(processor.contexts.get(i).getId(), i + 1);
            }
        }

        {
            // 第一个任务执行失败时挂起，此时第一个任务失败无法正常执行第二个任务
            processor.setLatch(new CountDownLatch(2));
            processor.contexts.clear();
            SetTaskModel model = new SetTaskModel();
            model.setRequestId(UUID.randomUUID().toString());
            TaskNodeModel nodeModel0 =
                buildTest("JoeKerouac1", 1, FailStrategy.PENDING, StrategyConst.ALL_PARENT_FINISH, null);
            TaskNodeModel nodeModel1 =
                buildTest("JoeKerouac2", 2, FailStrategy.PENDING, StrategyConst.ALL_PARENT_FINISH, null);

            nodeModel0.setAllChild(Collections.singletonList(nodeModel1));
            model.setFirstTask(nodeModel0);
            model.setLastTask(nodeModel1);
            flowService.addTask(model);

            Assert.assertFalse(processor.latch.await(3, TimeUnit.SECONDS));
            Assert.assertEquals(processor.latch.getCount(), 1);
            Assert.assertEquals(processor.contexts.size(), 1);

            // 校验执行顺序
            for (int i = 0; i < processor.contexts.size(); i++) {
                Assert.assertEquals(processor.contexts.get(i).getId(), i + 1);
            }
        }

        {
            // 第一个任务执行失败时忽略，并且第二个任务要求第一个任务执行必须成功，此时第一个任务失败无法正常执行第二个任务
            processor.setLatch(new CountDownLatch(2));
            processor.contexts.clear();
            SetTaskModel model = new SetTaskModel();
            model.setRequestId(UUID.randomUUID().toString());
            TaskNodeModel nodeModel0 =
                buildTest("JoeKerouac1", 1, FailStrategy.IGNORE, StrategyConst.ALL_PARENT_FINISH, null);
            TaskNodeModel nodeModel1 =
                buildTest("JoeKerouac2", 2, FailStrategy.PENDING, StrategyConst.ALL_PARENT_SUCCESS_STRATEGY, null);

            nodeModel0.setAllChild(Collections.singletonList(nodeModel1));
            model.setFirstTask(nodeModel0);
            model.setLastTask(nodeModel1);
            flowService.addTask(model);

            Assert.assertFalse(processor.latch.await(3, TimeUnit.SECONDS));
            Assert.assertEquals(processor.latch.getCount(), 1);
            Assert.assertEquals(processor.contexts.size(), 1);

            // 校验执行顺序
            for (int i = 0; i < processor.contexts.size(); i++) {
                Assert.assertEquals(processor.contexts.get(i).getId(), i + 1);
            }
        }

        {
            // 第一个任务执行失败时忽略，并且第二个任务要求0个父任务执行成功，此时第一个任务失败可以正常执行第二个任务
            processor.setLatch(new CountDownLatch(2));
            processor.contexts.clear();
            SetTaskModel model = new SetTaskModel();
            model.setRequestId(UUID.randomUUID().toString());
            TaskNodeModel nodeModel0 =
                buildTest("JoeKerouac1", 1, FailStrategy.IGNORE, StrategyConst.ALL_PARENT_FINISH, null);
            TaskNodeModel nodeModel1 =
                buildTest("JoeKerouac2", 2, FailStrategy.PENDING, StrategyConst.MIN_AMOUNT_PARENT_STRATEGY, "0");

            nodeModel0.setAllChild(Collections.singletonList(nodeModel1));
            model.setFirstTask(nodeModel0);
            model.setLastTask(nodeModel1);
            flowService.addTask(model);

            Assert.assertTrue(processor.latch.await(3, TimeUnit.SECONDS));
            Assert.assertEquals(processor.contexts.size(), 2);

            // 校验执行顺序
            for (int i = 0; i < processor.contexts.size(); i++) {
                Assert.assertEquals(processor.contexts.get(i).getId(), i + 1);
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
     * @param failStrategy
     *            失败时的策略
     * @param executeStrategy
     *            本节点执行时的要求
     * @param strategyContext
     *            strategyContext
     * @return 任务节点
     */
    private TaskNodeModel buildTest(String context, int id, FailStrategy failStrategy, String executeStrategy,
        String strategyContext) {
        TaskNodeModel model = new TaskNodeModel();
        model.setRequestId(UUID.randomUUID().toString());
        model.setData(new SpringTask(context, id));
        model.setFailStrategy(failStrategy);
        model.setExecuteStrategy(executeStrategy);
        model.setStrategyContext(strategyContext);
        return model;
    }

}
