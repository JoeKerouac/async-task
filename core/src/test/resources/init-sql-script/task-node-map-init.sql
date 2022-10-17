create table if not exists `task_node_map`
(
    `task_request_id` varchar(100) not null,
    `parent_node`     varchar(100) not null,
    `child_node`      varchar(100) not null,
    `id`              varchar(100) not null,
    `gmt_create_time` datetime     not null,
    `gmt_update_time` datetime     not null,
    `ext_map`         varchar(2000),
    primary key (`id`)
);

create index `idx_task_req` on `task_node_map` (`task_request_id`);
create index `idx_parent_req` on `task_node_map` (`parent_node`);
create index `idx_child_req` on `task_node_map` (`child_node`);


