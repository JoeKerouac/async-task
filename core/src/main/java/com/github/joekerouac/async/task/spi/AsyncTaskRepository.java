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
package com.github.joekerouac.async.task.spi;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import com.github.joekerouac.async.task.entity.AsyncTask;
import com.github.joekerouac.async.task.model.ExecStatus;
import com.github.joekerouac.async.task.model.TaskFinishCode;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public interface AsyncTaskRepository {

    /**
     * 保存任务
     * 
     * @param task
     *            待保存的任务
     * @return true表示保存成功，false表示主键冲突，保存失败
     */
    boolean save(@NotNull @Valid AsyncTask task);

    /**
     * 根据request id查询执行AsyncTask
     * 
     * @param requestId
     *            创建任务时的requestId
     * @return AsyncTask
     */
    AsyncTask selectByRequestId(@NotBlank String requestId);

    /**
     * 根据request id查询AsyncTask
     *
     * @param requestId
     *            创建任务时的requestId
     * @return AsyncTask
     */
    AsyncTask selectForUpdate(@NotBlank String requestId);

    /**
     * CAS更新，将指定任务的状态从期望值修改为目标值，同时将执行任务的IP修改为目标IP，注意：需要保证并发安全；如果当前存在事务，应该加入事务，如果当前没有事务，则不使用事务
     * 
     * @param requestId
     *            创建任务时的requestId
     * @param before
     *            任务当前期望值
     * @param after
     *            修改后的目标值
     * @param ip
     *            执行任务的IP
     * @return 大于0表示更新成功，否则表示更新失败
     */
    int casUpdate(@NotBlank String requestId, @NotNull ExecStatus before, @NotNull ExecStatus after,
        @NotBlank String ip);

    /**
     * CAS取消，将指定任务的状态从期望值修改为取消状态，同时将执行任务的IP修改为目标IP，注意：需要保证并发安全；如果当前存在事务，应该加入事务，如果当前没有事务，则不使用事务
     *
     * @param requestId
     *            创建任务时的requestId
     * @param before
     *            任务当前期望值
     * @param ip
     *            取消任务的IP
     * @return 大于0表示更新成功，否则表示更新失败
     */
    int casCancel(@NotBlank String requestId, @NotNull ExecStatus before, @NotBlank String ip);

    /**
     * 根据request id更新任务的状态、下次执行时间、当前重试次数、执行任务的IP
     * 
     * @param requestId
     *            创建任务时的requestId
     * @param status
     *            任务状态，为null表示不需要更新该字段
     * @param code
     *            任务执行结果，为null表示不需要更新该字段
     * @param execTime
     *            执行时间，为null表示不需要更新该字段
     * @param retry
     *            当前重试次数，为null表示不需要更新该字段
     * @param ip
     *            执行任务的IP，为null表示不需要更新该字段
     * @return 更新影响数据行数
     */
    int update(@NotBlank String requestId, ExecStatus status, TaskFinishCode code, LocalDateTime execTime,
        Integer retry, String ip);

    /**
     * 根据结束码查询指定截止日期前执行完毕的任务
     * 
     * @param processor
     *            任务所属处理器
     * @param finishCode
     *            结束码
     * @param dateTime
     *            执行时间截止日期
     * @param offset
     *            分页offset
     * @param limit
     *            分页大小
     * @return 符合条件的数据
     */
    List<AsyncTask> selectFinishPage(@NotBlank String processor, @NotNull TaskFinishCode finishCode,
        @NotNull LocalDateTime dateTime, @Min(0) int offset, @Min(1) @Max(200) int limit);

    /**
     * 根据requestId批量删除任务
     * 
     * @param requestIds
     *            requestId
     * @return 删除结果
     */
    int delete(Set<String> requestIds);

    /**
     * 统计状态为执行中、并且执行时间在指定时间之前的任务
     * 
     * @param execTime
     *            执行时间
     * @return 任务列表
     */
    List<AsyncTask> stat(LocalDateTime execTime);

    /**
     * 根据任务状态和执行时间查询任务，任务状态应该与传入状态一致，任务的执行时间应该小于等于传入的执行时间，任务应该按照执行时间升序排序，执行时间靠前的优先返回；
     *
     * @param status
     *            任务状态，不能为空；
     * @param dateTime
     *            指定执行时间；
     * @param skipTaskRequestIds
     *            需要跳过的任务request id集合，查询时应该从结果集中跳过这些ID
     * @param offset
     *            分页offset
     * @param limit
     *            分页大小
     * @param processorGroup
     *            processor分组
     * @param contain
     *            true表示查询的指定的processor分组的任务，false表示查询所有非processor分组中的任务
     * @return 符合条件的数据
     */
    List<AsyncTask> selectPage(ExecStatus status, LocalDateTime dateTime, Collection<String> skipTaskRequestIds,
        int offset, int limit, Set<String> processorGroup, boolean contain);

}
