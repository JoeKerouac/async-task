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
package com.github.joekerouac.async.task.db;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.github.joekerouac.common.tools.enums.EnumInterface;
import com.github.joekerouac.async.task.db.handler.EnumInterfaceTypeHandler;
import com.github.joekerouac.async.task.db.handler.ExtMapTypeHandler;
import com.github.joekerouac.async.task.db.handler.IntTypeHandler;
import com.github.joekerouac.async.task.db.handler.LocalDateTimeTypeHandler;
import com.github.joekerouac.async.task.db.handler.StringTypeHandler;
import com.github.joekerouac.async.task.entity.common.ExtMap;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public class TypeHandlerRegistry {

    @SuppressWarnings("rawtypes")
    private static final Map<Class<?>, TypeHandler> typeHandlerMap = new ConcurrentHashMap<>();

    private static final List<Class<?>> USE_PARENT = new ArrayList<>();

    static {
        USE_PARENT.add(EnumInterface.class);
        register(EnumInterface.class, new EnumInterfaceTypeHandler());
        register(ExtMap.class, new ExtMapTypeHandler());
        register(Integer.class, new IntTypeHandler());
        register(LocalDateTime.class, new LocalDateTimeTypeHandler());
        register(String.class, new StringTypeHandler());
    }

    @SuppressWarnings("rawtypes")
    public static void register(Class<?> clazz, TypeHandler handler) {
        typeHandlerMap.put(clazz, handler);
    }

    @SuppressWarnings("unchecked")
    public static <T> TypeHandler<T> get(Class<?> clazz) {
        @SuppressWarnings("rawtypes")
        TypeHandler typeHandler = typeHandlerMap.get(clazz);

        // 这里主要是处理TypeHandler注册时使用的是父类型，但是获取的时候实际是使用子类型获取的这种场景
        if (typeHandler == null) {
            for (final Class<?> aClass : USE_PARENT) {
                if (aClass.isAssignableFrom(clazz)) {
                    typeHandler = typeHandlerMap.get(aClass);

                    if (typeHandler != null) {
                        typeHandlerMap.put(clazz, typeHandler);
                    }

                    return typeHandler;
                }
            }
        }

        return typeHandler;
    }

}
