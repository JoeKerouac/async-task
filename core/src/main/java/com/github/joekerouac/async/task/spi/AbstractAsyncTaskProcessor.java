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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.Map;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import com.github.joekerouac.async.task.model.ExecResult;
import com.github.joekerouac.async.task.model.TaskFinishCode;
import com.github.joekerouac.common.tools.reflect.type.AbstractTypeReference;
import com.github.joekerouac.common.tools.util.JsonUtil;

/**
 * 异步任务执行器
 * 
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 * @param <T>
 *            任务实际类型
 */
public abstract class AbstractAsyncTaskProcessor<T> {

    /**
     * 默认重试时间间隔，单位毫秒
     */
    protected long[] retryTimeInterval = new long[] {0, 1000, 1000 * 5, 1000 * 10, 1000 * 10, 1000 * 30};

    /**
     * 对应任务类型
     */
    protected final Type type;

    /**
     * 默认的处理器可以处理的任务名
     */
    protected final String defaultProcessorName;

    protected AbstractAsyncTaskProcessor() {
        Type superClass = getClass().getGenericSuperclass();
        while (superClass instanceof Class<?>) {
            if (superClass.equals(Object.class)) {
                throw new IllegalArgumentException(
                    "Internal error: TypeReference constructed without actual type information");
            }
            superClass = ((Class<?>)superClass).getGenericSuperclass();
        }

        type = ((ParameterizedType)superClass).getActualTypeArguments()[0];
        String typeName = type.getTypeName();
        typeName = typeName.lastIndexOf(".") < 0 ? typeName : typeName.substring(typeName.lastIndexOf(".") + 1);
        // 处理内部类、代理类等
        typeName = typeName.lastIndexOf("$") < 0 ? typeName : typeName.substring(typeName.lastIndexOf("$") + 1);
        defaultProcessorName =
            typeName.lastIndexOf(".") < 0 ? typeName : typeName.substring(typeName.lastIndexOf(".") + 1);
    }

    /**
     * 任务处理，需要用户实现
     * 
     * @param requestId
     *            创建任务时的requestId
     * @param context
     *            创建任务时的任务详情
     * @param cache
     *            缓存，可以传递给{@link #afterProcess(String, Object, TaskFinishCode, Throwable, Map)}
     * @return 任务执行结果，返回null将认为任务执行成功；
     * @throws Throwable
     *             任务执行过程中的异常，异常后任务将被重试（如果任务允许被重试）
     */
    public abstract ExecResult process(@NotBlank String requestId, @NotNull T context,
        @NotNull Map<String, Object> cache) throws Throwable;

    /**
     * 如果任务执行超时，是否允许重启
     * 
     * @param requestId
     *            任务幂等ID
     * @param context
     *            任务
     * @return true表示不允许重启，此时只能人工介入排查，对于无法幂等的任务建议返回false
     */
    public boolean canReExec(String requestId, @NotNull T context) {
        return false;
    }

    /**
     * 该处理器可以处理的任务类型
     * 
     * @return 该处理器可以处理的任务类型数组，不能返回null
     */
    public String[] processors() {
        return new String[] {defaultProcessorName};
    }

    /**
     * 异步任务执行完成后、更新任务数据库前调用（注意：如果任务还需要重试则也不会调用），该方法抛出的异常将影响任务的状态，任务无法结束，将会重新执行，所以请不要抛出异常；
     * 
     * 注意：如果出现processor找不到、序列化异常这些异常时时不会调用本方法的；
     *
     * 注意：如果本方法抛出异常，将会导致不可预知错误；例如：如果当前用户任务重试达到上限结束，此时任务会结束，结束前会调用该方法，而调用该方法的时候该方法抛出异常会导致任务再次重试而不受用户设置的重试次数限制；
     * 
     * @param requestId
     *            创建任务时的requestId
     * @param context
     *            创建任务是的任务详情
     * @param code
     *            任务执行结果
     * @param processException
     *            执行过程中发生的异常
     * @param cache
     *            执行过程中的缓存
     */
    public void afterProcess(@NotBlank String requestId, @NotNull T context, @NotNull TaskFinishCode code,
        Throwable processException, @NotNull Map<String, Object> cache) {

    }

    /**
     * 将任务序列化为字符串
     *
     * @param context
     *            任务数据
     * @return 任务数据序列化后的字符串
     */
    public String serialize(@NotNull Object context) {
        return new String(JsonUtil.write(context), Charset.defaultCharset());
    }

    /**
     * 将任务数据重新反序列化为对象
     *
     * @param requestId
     *            创建任务时的requestId
     * @param context
     *            任务数据
     * @param cache
     *            执行过程中的缓存
     * @return 任务模型
     */
    public T deserialize(@NotBlank String requestId, @NotBlank String context, @NotNull Map<String, Object> cache) {
        return JsonUtil.read(context.getBytes(Charset.defaultCharset()), new AbstractTypeReference<T>() {
            @Override
            public Type getType() {
                return AbstractAsyncTaskProcessor.this.type;
            }
        });
    }

    /**
     * 指定异常是否可以重试
     * 
     * @param requestId
     *            创建任务时的幂等请求ID
     * @param context
     *            任务
     * @param throwable
     *            当前异常
     * @param cache
     *            执行过程中的缓存
     * @return true表示可以重试
     */
    public boolean canRetry(@NotBlank String requestId, @NotNull T context, @NotNull Throwable throwable,
        @NotNull Map<String, Object> cache) {
        return true;
    }

    /**
     * 获取任务下次重试时间
     *
     * @param requestId
     *            创建任务时的requestId
     * @param retry
     *            当前重试次数，包含本次重试，例如第一次重试这个传入1；
     * @param context
     *            任务数据
     * @param cache
     *            执行过程中的缓存
     * @return 下次重试时间间隔，小于等于0表示立即重试，单位毫秒
     */
    public long nextExecTimeInterval(@NotBlank String requestId, @Min(0) int retry, @NotNull T context,
        @NotNull Map<String, Object> cache) {
        int index = retry - 1;
        // 这里使用局部变量重新引用，防止并发时外部反射替换字段导致后一步执行异常
        long[] retryTimeInterval = this.retryTimeInterval;
        return index < retryTimeInterval.length ? retryTimeInterval[index]
            : retryTimeInterval[retryTimeInterval.length - 1];
    }

    /**
     * 是否自动清理执行成功和被取消的任务
     * 
     * @return true表示自动清理执行成功和被取消的任务
     */
    public boolean autoClear() {
        return false;
    }

    /**
     * 如果{@link #autoClear()}为true则该方法有效，表示保留多少小时内执行完的任务，执行完成后超出该时间的任务将会被清理
     * 
     * @return 任务执行完成后的保留时间，单位小时
     */
    public int reserve() {
        return 24 * 3;
    }

}
