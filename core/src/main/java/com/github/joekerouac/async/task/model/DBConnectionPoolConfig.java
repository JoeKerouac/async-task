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
package com.github.joekerouac.async.task.model;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public class DBConnectionPoolConfig {

    /**
     * 最小连接池数量
     */
    private int minIdle;

    /**
     * 最大连接池数量
     */
    private int maxActive;

    /**
     * 配置获取连接等待超时的时间
     */
    private long maxWait;

    /**
     * 配置间隔多久才进行一次检测，检测需要关闭的空闲连接，单位是毫秒
     */
    private long timeBetweenEvictionRunsMillisl;

    /**
     * 配置一个连接在池中最小生存的时间，单位是毫秒
     */
    private long minEvictableIdleTimeMillis;

    /**
     * 测试连接
     */
    private String validationQuery;

    /**
     * 单位：秒，检测连接是否有效的超时时间
     */
    private int validationQueryTimeout;

    /**
     * 申请连接的时候检测，建议配置为true，不影响性能，并且保证安全性
     */
    private boolean testWhileIdle;

    /**
     * 获取连接时执行检测，建议关闭，影响性能
     */
    private boolean testOnBorrow;

    /**
     * 归还连接时执行检测，建议关闭，影响性能
     */
    private boolean testOnReturn;

}
