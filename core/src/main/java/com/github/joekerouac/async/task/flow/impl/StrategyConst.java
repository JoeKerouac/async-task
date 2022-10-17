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
package com.github.joekerouac.async.task.flow.impl;

/**
 * 节点策略名
 * 
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public final class StrategyConst {

    /**
     * 子节点在指定requestId的父级全部执行成功时才能执行，如果有一个父级pending或者error，那么节点都会pending；
     * 
     * 执行策略context是指定父节点的requestId列表，多个requestId之间以英文逗号分隔；
     */
    public static final String SPECIAL_PARENT_STRATEGY = "SpecialParent";

    /**
     * 只要执行成功的父节点数量满足我们的要求节点即可执行，无需等待所有父节点执行完毕；如果已经确定满足不了需求，那么节点会pending，例如我们要求10个节
     * 点中最少5个成功，但是现在已知error和pending的节点总数已经大于5了，那么节点将会直接pending；
     * 
     * 执行策略context是执行成功的父节点数量最小值，number字符串；
     */
    public static final String MIN_AMOUNT_PARENT_STRATEGY = "MinAmountParent";

    /**
     * 必须所有父节点执行成功才可执行，如果任意一个父节点执行error或者pending，那么节点会pending；
     * 
     * 执行策略context要求为空；
     */
    public static final String ALL_PARENT_SUCCESS_STRATEGY = "AllParentSuccess";

    /**
     * 必须所有父节点执行完成才可执行，如果任意一个父节点pending，那么节点会pending，注意，这个不要求父节点必须是SUCCESS，父节点是ERROR也是可以校验通过的；
     * 
     * 执行策略context要求为空；
     */
    public static final String ALL_PARENT_FINISH = "AllParentFinish";

}
