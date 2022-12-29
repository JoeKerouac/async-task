package com.github.joekerouac.async.task.model;

import lombok.Data;

/**
 * 任务清理说明
 *
 * @author JoeKerouac
 * @date 2022-12-29 15:10
 * @since 2.0.0
 */
@Data
public class ClearDesc {

    /**
     * 任务执行后最少保留时间，单位小时
     */
    private int reserve;

    /**
     * 要清理的任务所属处理器
     */
    private String processor;

}
