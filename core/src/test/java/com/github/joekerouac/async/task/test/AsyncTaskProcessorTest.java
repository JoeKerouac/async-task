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

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.testng.Assert;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import com.github.joekerouac.async.task.entity.AsyncTask;
import com.github.joekerouac.async.task.model.ExecResult;
import com.github.joekerouac.async.task.model.ExecStatus;
import com.github.joekerouac.async.task.model.TaskFinishCode;
import com.github.joekerouac.async.task.model.TransStrategy;
import com.github.joekerouac.async.task.spi.AbstractAsyncTaskProcessor;
import com.github.joekerouac.async.task.test.model.RetryTask;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public class AsyncTaskProcessorTest extends TestEngine {

    @BeforeSuite
    @Override
    public void init() throws Exception {
        super.init();
    }

    @AfterSuite
    @Override
    public void destroy() {
        super.destroy();
    }

    @Test
    public void testRetry() throws Exception {
        // ????????????????????????????????????????????????????????????3????????????
        String processorName = "RetryOverFlow";
        int maxRetry = 3;
        CountDownLatch latch = new CountDownLatch(maxRetry + 1);

        asyncTaskService.addProcessor(new AbstractAsyncTaskProcessor<RetryTask>() {
            @Override
            public ExecResult process(final String requestId, final RetryTask context, final Map<String, Object> cache)
                throws Throwable {
                latch.countDown();
                return ExecResult.RETRY;
            }

            @Override
            public String[] processors() {
                return new String[] {processorName};
            }

            @Override
            public long nextExecTimeInterval(final String requestId, final int retry, final RetryTask context,
                final Map<String, Object> cache) {
                return 0;
            }
        });

        String requestId = UUID.randomUUID().toString();
        RetryTask task = new RetryTask();
        task.setAge(18);
        asyncTaskService.addTask(requestId, task, maxRetry, LocalDateTime.now(), processorName, TransStrategy.SUPPORTS);

        boolean flag = latch.await(10, TimeUnit.SECONDS);
        Assert.assertTrue(flag);
        // ??????latch???????????????????????????????????????????????????????????????????????????1???????????????????????????????????????
        Thread.sleep(500);
        AsyncTask asyncTask = repository.selectByRequestId(requestId);
        Assert.assertEquals(asyncTask.getMaxRetry(), maxRetry);
        Assert.assertEquals(asyncTask.getRetry(), maxRetry);
        Assert.assertEquals(asyncTask.getTaskFinishCode(), TaskFinishCode.RETRY_OVERFLOW);
        System.out.println("?????????????????????");
    }

    @Test
    public void testCannotRetry() throws Exception {
        // ???????????????????????????
        int maxRetry = 3;
        String processorName = "CannotRetry";
        CountDownLatch latch = new CountDownLatch(1);

        asyncTaskService.addProcessor(new AbstractAsyncTaskProcessor<RetryTask>() {
            @Override
            public ExecResult process(final String requestId, final RetryTask context, final Map<String, Object> cache)
                throws Throwable {
                latch.countDown();
                throw new RuntimeException("cannot retry");
            }

            @Override
            public String[] processors() {
                return new String[] {processorName};
            }

            @Override
            public boolean canRetry(final String requestId, final RetryTask context, final Throwable throwable,
                final Map<String, Object> cache) {
                return false;
            }
        });

        String requestId = UUID.randomUUID().toString();
        RetryTask task = new RetryTask();
        task.setAge(18);
        asyncTaskService.addTask(requestId, task, maxRetry, LocalDateTime.now(), processorName, TransStrategy.SUPPORTS);

        boolean flag = latch.await(10, TimeUnit.SECONDS);
        Assert.assertTrue(flag);
        // ??????latch???????????????????????????????????????????????????????????????????????????1???????????????????????????????????????
        Thread.sleep(500);
        AsyncTask asyncTask = repository.selectByRequestId(requestId);
        Assert.assertEquals(asyncTask.getMaxRetry(), maxRetry);
        // ????????????????????????????????????????????????????????????????????????0
        Assert.assertEquals(asyncTask.getRetry(), 0);
        Assert.assertEquals(asyncTask.getTaskFinishCode(), TaskFinishCode.CANNOT_RETRY);
    }

    @Test
    public void testUserError() throws Exception {
        // ??????????????????error?????????
        int maxRetry = 3;
        String processorName = "UserError";
        CountDownLatch latch = new CountDownLatch(1);

        asyncTaskService.addProcessor(new AbstractAsyncTaskProcessor<RetryTask>() {
            @Override
            public ExecResult process(final String requestId, final RetryTask context, final Map<String, Object> cache)
                throws Throwable {
                latch.countDown();
                return ExecResult.ERROR;
            }

            @Override
            public String[] processors() {
                return new String[] {processorName};
            }
        });

        String requestId = UUID.randomUUID().toString();
        RetryTask task = new RetryTask();
        task.setAge(18);
        asyncTaskService.addTask(requestId, task, maxRetry, LocalDateTime.now(), processorName, TransStrategy.SUPPORTS);

        boolean flag = latch.await(10, TimeUnit.SECONDS);
        Assert.assertTrue(flag);
        // ??????latch???????????????????????????????????????????????????????????????????????????1???????????????????????????????????????
        Thread.sleep(500);
        AsyncTask asyncTask = repository.selectByRequestId(requestId);
        Assert.assertEquals(asyncTask.getMaxRetry(), maxRetry);
        // ????????????????????????????????????????????????????????????????????????0
        Assert.assertEquals(asyncTask.getRetry(), 0);
        Assert.assertEquals(asyncTask.getTaskFinishCode(), TaskFinishCode.USER_ERROR);
    }

    @Test
    public void generateTest() throws Exception {
        AsyncTask task = new AsyncTask();
        task.setRequestId(UUID.randomUUID().toString());
        task.setId("123");
        task.setTask("{age: '18'}");
        task.setMaxRetry(6);
        task.setExecTime(LocalDateTime.now());
        task.setProcessor("unknown");
        task.setRetry(0);
        // ?????????????????????????????????????????????????????????????????????????????????
        task.setStatus(ExecStatus.FINISH);
        task.setTaskFinishCode(TaskFinishCode.SUCCESS);
        task.setCreateIp("123");
        task.setExecIp("123");

        repository.save(task);
        AsyncTask taskFromDb = repository.selectByRequestId(task.getRequestId());
        equals(task, taskFromDb);
        Assert.assertEquals(task.getGmtUpdateTime(), taskFromDb.getGmtUpdateTime());

        int updateEffective = repository.casUpdate(task.getRequestId(), ExecStatus.INIT, ExecStatus.FINISH, "123");
        Assert.assertEquals(updateEffective, 0);
        // ??????sleep10????????????????????????gmt_update_time???????????????????????????
        Thread.sleep(10);
        updateEffective = repository.casUpdate(task.getRequestId(), ExecStatus.FINISH, ExecStatus.WAIT, "456");
        Assert.assertEquals(updateEffective, 1);
        task.setStatus(ExecStatus.WAIT);
        task.setExecIp("456");

        // ??????????????????????????????????????????????????????????????????
        taskFromDb = repository.selectByRequestId(task.getRequestId());
        equals(task, taskFromDb);
        Assert.assertNotEquals(task.getGmtUpdateTime(), taskFromDb.getGmtUpdateTime());

        // ??????task????????????gmt_update_time
        task = taskFromDb;
        // ??????sleep10????????????????????????gmt_update_time???????????????????????????
        Thread.sleep(10);
        // ??????????????????
        LocalDateTime execTime = LocalDateTime.now().plus(1, ChronoUnit.HOURS);
        updateEffective = repository.update(task.getRequestId(), ExecStatus.FINISH, TaskFinishCode.CANNOT_RETRY,
            execTime, null, null);
        Assert.assertEquals(updateEffective, 1);
        task.setStatus(ExecStatus.FINISH);
        task.setTaskFinishCode(TaskFinishCode.CANNOT_RETRY);
        task.setExecTime(execTime);
        taskFromDb = repository.selectByRequestId(task.getRequestId());
        equals(task, taskFromDb);
        Assert.assertNotEquals(task.getGmtUpdateTime(), taskFromDb.getGmtUpdateTime());

        // ??????task????????????gmt_update_time
        task = taskFromDb;
        // ??????sleep10????????????????????????gmt_update_time???????????????????????????
        Thread.sleep(10);

        updateEffective = repository.update(task.getRequestId(), null, null, null, 5, "789");
        Assert.assertEquals(updateEffective, 1);
        task.setRetry(5);
        task.setExecIp("789");
        taskFromDb = repository.selectByRequestId(task.getRequestId());
        equals(task, taskFromDb);
        Assert.assertNotEquals(task.getGmtUpdateTime(), taskFromDb.getGmtUpdateTime());

        List<AsyncTask> asyncTasks =
            repository.selectPage(ExecStatus.FINISH, LocalDateTime.now(), Collections.emptyList(), 0, 10);
        Assert.assertTrue(asyncTasks.isEmpty());

        asyncTasks = repository.selectPage(ExecStatus.FINISH, execTime, Collections.emptyList(), 0, 10);
        Assert.assertFalse(asyncTasks.isEmpty());
        equals(task, asyncTasks.get(0));

        // ??????????????????
        Throwable throwable = null;
        try {
            repository.save(task);
        } catch (Throwable e) {
            throwable = e;
        }

        Assert.assertNotNull(throwable);
    }

}
