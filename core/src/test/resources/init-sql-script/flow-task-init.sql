create table if not exists `flow_task`
(
    `request_id`      varchar(100) not null,
    `type`            varchar(100) not null,
    `first_task_id`   varchar(100) not null,
    `last_task_id`    varchar(100) not null,
    `status`          varchar(100) not null,
    `id`              varchar(100) not null,
    `gmt_create_time` datetime(6)  not null,
    `gmt_update_time` datetime(6)  not null,
    `ext_map`         varchar(2000),
    primary key (`id`)
);

create unique index `idx_req` ON `flow_task` (`request_id`);
create unique index `idx_status` ON `flow_task` (`type`);
