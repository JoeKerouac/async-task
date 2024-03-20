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
package com.github.joekerouac.async.task.test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.github.joekerouac.async.task.AsyncTaskService;
import com.github.joekerouac.async.task.starter.annotations.EnableAsyncTask;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
@SpringBootTest(classes = SpringTest.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnableAsyncTask
@SpringBootApplication
public class SpringTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private AsyncTaskService service;

    @Autowired
    private SpringTaskProcessor processor;

    @Test
    public void test() throws Exception {
        SpringTask task = new SpringTask();
        task.setAge(18);
        service.addTask(UUID.randomUUID().toString(), task);
        boolean flag = processor.latch.await(3000, TimeUnit.MILLISECONDS);
        Assert.assertTrue(flag);
        Assert.assertEquals(processor.task, task);
    }

}
