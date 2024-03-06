create table if not exists `async_task`
(
    `request_id`       varchar(100)  not null,
    `task`             varchar(2000) not null,
    `max_retry`        int           not null,
    `exec_time`        datetime(6)   not null,
    `processor`        varchar(100)  not null,
    `retry`            int           not null,
    `status`           varchar(100)  not null,
    `task_finish_code` varchar(100)  not null,
    `create_ip`        varchar(100)  not null,
    `exec_ip`          varchar(100)  not null,
    `id`               varchar(100)  not null,
    `gmt_create_time`  datetime(6)   not null,
    `gmt_update_time`  datetime(6)   not null,
    `ext_map`          varchar(2000),
    primary key (`id`)

);

create unique index `idx_req` ON `async_task` (`request_id`);
create index `idx_load` ON `async_task` (`status`, `exec_time`, `processor`);

