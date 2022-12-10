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
package com.github.joekerouac.async.task;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import com.github.joekerouac.common.tools.log.Logger;
import com.github.joekerouac.common.tools.log.LoggerFactory;
import com.github.joekerouac.common.tools.net.HostInfo;
import com.github.joekerouac.common.tools.validator.ValidationService;
import com.github.joekerouac.common.tools.validator.ValidationServiceImpl;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public final class Const {

    /**
     * 默认的监控日志
     */
    public static final Logger DEFAULT_ASYNC_MONITOR_LOGGER = LoggerFactory.getLogger("DEFAULT_ASYNC_MONITOR");

    /**
     * 外部系统输入默认编码以及对外输出默认编码
     */
    public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    /**
     * 校验服务
     */
    public static final ValidationService VALIDATION_SERVICE = new ValidationServiceImpl();

    /**
     * 本机IP
     */
    public static final String IP = HostInfo.getInstance().getHostAddress();

    /**
     * null
     */
    public static final String NULL = "N/A";

}
