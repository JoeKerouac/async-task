create table if not exists `flow_task`
(
    `request_id`      varchar(200) not null comment '幂等ID',
    `type`            varchar(100) not null comment '流式任务类型，枚举值',
    `first_task_id`   varchar(100) not null comment '流式任务第一个任务的request id',
    `last_task_id`    varchar(100) not null comment '流式任务最后一个任务的request id',
    `status`          varchar(100) not null comment '流式任务状态，枚举值',
    `id`              varchar(100) not null,
    `gmt_create_time` datetime     not null,
    `gmt_update_time` datetime     not null,
    `ext_map`         varchar(2000),
    primary key (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 comment '流式任务主任务表';

create unique index `idx_req` ON `flow_task` (`request_id`);
create unique index `idx_status` ON `flow_task` (`type`, `status`, `gmt_update_time`);


create table if not exists `task_node`
(
    `request_id`       varchar(200)  not null comment '幂等ID',
    `task_request_id`  varchar(200)  not null comment '该节点对应的主任务的幂等ID',
    `node_data`        varchar(2000) not null comment '实际的节点任务数据',
    `processor`        varchar(100)  not null comment '对应的处理器名',
    `status`           varchar(100)  not null comment '节点任务状态，枚举值',
    `fail_strategy`    varchar(100)  not null comment '节点失败策略',
    `execute_strategy` varchar(100)  not null comment '节点执行策略',
    `strategy_context` varchar(1000) not null comment '节点执行策略上下文，允许为null',
    `max_retry`        int           not null comment '最大重试次数',
    `id`               varchar(100)  not null,
    `gmt_create_time`  datetime      not null,
    `gmt_update_time`  datetime      not null,
    `ext_map`          varchar(2000),
    primary key (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 comment '流式任务节点表';

create unique index `idx_req` ON `task_node` (`request_id`);
create index `idx_task_req_status` ON `task_node` (`task_request_id`, `status`, `gmt_update_time`);



create table if not exists `task_node_map`
(
    `task_request_id` varchar(200) not null comment '节点对应的主任务的幂等ID',
    `parent_node`     varchar(200) not null comment '父节点的幂等ID',
    `child_node`      varchar(200) not null comment '子节点的幂等ID',
    `id`              varchar(100) not null,
    `gmt_create_time` datetime     not null,
    `gmt_update_time` datetime     not null,
    `ext_map`         varchar(2000),
    primary key (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 comment '流式任务节点关系表';

create index `idx_task_req` on `task_node_map` (`task_request_id`);
create index `idx_parent_req` on `task_node_map` (`parent_node`);
create index `idx_child_req` on `task_node_map` (`child_node`);

