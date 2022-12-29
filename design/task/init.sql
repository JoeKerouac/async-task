create table if not exists `async_task`
(
    `request_id`       varchar(200)  not null comment '幂等ID',
    `task`             varchar(3000) not null comment '任务详情',
    `max_retry`        int           not null comment '最大可重试次数，-1表示无限重试',
    `exec_time`        datetime      not null comment '任务开始执行时间，重试时会更新',
    `processor`        varchar(100)  not null comment '任务执行器',
    `retry`            int           not null comment '当前重试次数',
    `status`           varchar(100)  not null comment '任务状态',
    `task_finish_code` varchar(100)  not null comment '任务执行结果码，任务执行完毕后才有意义，解释任务为什么结束',
    `create_ip`        varchar(100)  not null comment '创建任务的服务所在的机器IP',
    `exec_ip`          varchar(100)  not null comment '执行任务的服务所在的机器IP',
    `id`               varchar(100)  not null,
    `gmt_create_time`  datetime      not null,
    `gmt_update_time`  datetime      not null,
    `ext_map`          varchar(2000),
    primary key (`id`)

) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 comment '异步任务表';

create unique index `idx_req` ON `async_task` (`request_id`);
create index `idx_load` ON `async_task` (`status`, `exec_time`) comment '捞取任务使用该索引';
create index `idx_clear` ON `async_task` (`processor`, `task_finish_code`, `status`, `exec_time`) comment '清理任务使用该索引';



