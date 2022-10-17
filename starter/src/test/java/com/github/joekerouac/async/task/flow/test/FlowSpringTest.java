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
        SetTaskModel model = new SetTaskModel();
        model.setRequestId(UUID.randomUUID().toString());
        TaskNodeModel nodeModel0 = buildTest("JoeKerouac1", 1);
        TaskNodeModel nodeModel1 = buildTest("JoeKerouac2", 2);

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

    /**
     * 构建一个随机的任务节点
     *
     * @param context
     *            name
     * @param id
     *            age
     * @return 任务节点
     */
    private TaskNodeModel buildTest(String context, int id) {
        TaskNodeModel model = new TaskNodeModel();
        model.setRequestId(UUID.randomUUID().toString());
        model.setData(new SpringTask(context, id));
        model.setFailStrategy(FailStrategy.PENDING);
        model.setExecuteStrategy(StrategyConst.ALL_PARENT_FINISH);
        return model;
    }

}
