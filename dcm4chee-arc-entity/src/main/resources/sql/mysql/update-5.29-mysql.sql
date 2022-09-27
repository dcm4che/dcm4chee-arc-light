-- can be applied on archive running archive 5.28
create table key_value2
(
    pk bigint not null auto_increment,
    content_type varchar(255) not null,
    created_time datetime not null,
    key_name varchar(255) not null,
    updated_time datetime not null,
    username varchar(255),
    key_value varchar(4000) not null,
    primary key (pk)
);

alter table key_value2
    add constraint UK_4gq7ksl296rsm6ap9hjrogv3g unique (key_name);

create index UK_hy6xxbt6wi79kbxt6wsqhv77p on key_value2 (username(64));
create index UK_5gcbr7nnck6dxrmml1s3arpna on key_value2 (updated_time);

-- part 2: shall be applied on stopped archive before starting 5.28

-- part 3: can be applied on already running archive 5.28
drop table key_value;