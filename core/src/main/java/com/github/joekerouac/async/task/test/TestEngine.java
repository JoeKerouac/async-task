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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;

import javax.sql.DataSource;

import org.testng.Assert;

import com.alibaba.druid.pool.DruidDataSource;
import com.github.joekerouac.async.task.AsyncTaskService;
import com.github.joekerouac.async.task.db.AsyncTransactionManagerImpl;
import com.github.joekerouac.async.task.entity.AsyncTask;
import com.github.joekerouac.async.task.impl.AsyncTaskRepositoryImpl;
import com.github.joekerouac.async.task.impl.DefaultProcessorRegistry;
import com.github.joekerouac.async.task.impl.MonitorServiceAdaptor;
import com.github.joekerouac.async.task.impl.SimpleConnectionManager;
import com.github.joekerouac.async.task.model.AsyncServiceConfig;
import com.github.joekerouac.async.task.model.AsyncTaskExecutorConfig;
import com.github.joekerouac.async.task.model.AsyncThreadPoolConfig;
import com.github.joekerouac.async.task.service.AsyncTaskServiceImpl;
import com.github.joekerouac.async.task.spi.AsyncTaskRepository;
import com.github.joekerouac.async.task.spi.AsyncTransactionManager;
import com.github.joekerouac.async.task.spi.ProcessorRegistry;
import com.github.joekerouac.common.tools.io.IOUtils;
import com.github.joekerouac.common.tools.resource.impl.ClassPathResource;
import com.github.joekerouac.common.tools.string.StringUtils;

/**
 * 方便其他系统开发测试用例的工具类
 *
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public class TestEngine {

    /**
     * 全局共享数据源
     */
    protected DataSource dataSource;

    protected AsyncTaskRepository repository;

    protected AsyncTaskService asyncTaskService;

    protected AsyncServiceConfig asyncServiceConfig;

    protected AsyncTransactionManager transactionManager;

    protected ProcessorRegistry processorRegistry;

    /**
     * 初始化
     * 
     * @throws Exception
     *             异常
     */
    public void init() throws Exception {
        this.dataSource = initDataSource(StringUtils.format("{}-test.db", getClass().getSimpleName()));
        this.transactionManager = new AsyncTransactionManagerImpl(new SimpleConnectionManager(dataSource), null);
        repository = new AsyncTaskRepositoryImpl(transactionManager);
        processorRegistry = new DefaultProcessorRegistry();

        AsyncThreadPoolConfig asyncThreadPoolConfig = new AsyncThreadPoolConfig();
        asyncThreadPoolConfig.setCorePoolSize(3);
        asyncThreadPoolConfig.setThreadName("异步任务线程");
        asyncThreadPoolConfig.setPriority(1);
        asyncThreadPoolConfig.setDefaultContextClassLoader(Thread.currentThread().getContextClassLoader());

        AsyncTaskExecutorConfig asyncTaskExecutorConfig = new AsyncTaskExecutorConfig();
        asyncTaskExecutorConfig.setCacheQueueSize(10);
        asyncTaskExecutorConfig.setLoadThreshold(3);
        asyncTaskExecutorConfig.setLoadInterval(1000 * 5);
        asyncTaskExecutorConfig.setMonitorInterval(1000 * 5);
        asyncTaskExecutorConfig.setExecTimeout(1000 * 30);
        asyncTaskExecutorConfig.setLoadTaskFromRepository(false);
        asyncTaskExecutorConfig.setThreadPoolConfig(asyncThreadPoolConfig);

        asyncServiceConfig = new AsyncServiceConfig();
        asyncServiceConfig.setDefaultExecutorConfig(asyncTaskExecutorConfig);
        asyncServiceConfig.setTransactionManager(transactionManager);
        asyncServiceConfig.setRepository(repository);
        asyncServiceConfig.setIdGenerator(() -> UUID.randomUUID().toString());
        asyncServiceConfig.setProcessorRegistry(processorRegistry);
        asyncServiceConfig.setMonitorService(new MonitorServiceAdaptor() {
            @Override
            public void uncaughtException(Thread thread, Throwable e) {
                e.printStackTrace();
            }
        });

        AsyncTaskService service = new AsyncTaskServiceImpl(asyncServiceConfig);
        service.start();
        asyncTaskService = service;
    }

    /**
     * 销毁
     */
    public void destroy() {
        asyncTaskService.stop();
    }

    /**
     * 初始化一个sqlite数据库，并返回其数据源
     *
     * @param dbFile
     *            db文件位置
     * @return 数据源
     */
    @SuppressWarnings("ConstantConditions")
    public static DataSource initDataSource(String dbFile) {
        File file = new File(dbFile);
        if (file.exists()) {
            if (!file.delete()) {
                throw new RuntimeException("老DB文件删除失败");
            }
        }

        // 使用sqlite嵌入式数据库作为我们测试用例的数据源
        DruidDataSource dataSource = new DruidDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dbFile);

        File scriptDir;
        try {
            scriptDir = new File(new ClassPathResource("init-sql-script").getUrl().getFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (Connection conn = dataSource.getConnection()) {
            // 初始化数据库，放到一个文件里变会初始化失败，似乎sqlite不支持多个sql一次性执行，所以这里我们把不同的表放到不同的文件中
            for (final File initScript : scriptDir.listFiles()) {
                InputStream inputStream = new FileInputStream(initScript);
                String initSql = new String(IOUtils.read(inputStream, true), StandardCharsets.UTF_8);
                Statement statement = conn.createStatement();
                statement.execute(initSql);
            }
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }

        return dataSource;
    }

    protected void equals(AsyncTask task0, AsyncTask task1) {
        Assert.assertEquals(task0.getId(), task1.getId());
        Assert.assertEquals(task0.getGmtCreateTime(), task1.getGmtCreateTime());
        Assert.assertEquals(task0.getTask(), task1.getTask());
        Assert.assertEquals(task0.getMaxRetry(), task1.getMaxRetry());
        Assert.assertEquals(task0.getExecTime(), task1.getExecTime());
        Assert.assertEquals(task0.getProcessor(), task1.getProcessor());
        Assert.assertEquals(task0.getRetry(), task1.getRetry());
        Assert.assertEquals(task0.getStatus(), task1.getStatus());
        Assert.assertEquals(task0.getTaskFinishCode(), task1.getTaskFinishCode());
        Assert.assertEquals(task0.getCreateIp(), task1.getCreateIp());
        Assert.assertEquals(task0.getExecIp(), task1.getExecIp());
    }

}
