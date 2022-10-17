create table if not exists `task_node`
(
    `request_id`       varchar(100)  not null,
    `task_request_id`  varchar(100)  not null,
    `node_data`        varchar(2000) not null,
    `processor`        varchar(100)  not null,
    `status`           varchar(100)  not null,
    `fail_strategy`    varchar(100)  not null,
    `execute_strategy` varchar(100)  not null,
    `strategy_context` varchar(1000) not null,
    `max_retry`        int           not null,
    `id`               varchar(100)  not null,
    `gmt_create_time`  datetime      not null,
    `gmt_update_time`  datetime      not null,
    `ext_map`          varchar(2000),
    primary key (`id`)
);

create unique index `idx_req` ON `task_node` (`request_id`);
create index `idx_task_req_status` ON `task_node` (`task_request_id`, `status`);



