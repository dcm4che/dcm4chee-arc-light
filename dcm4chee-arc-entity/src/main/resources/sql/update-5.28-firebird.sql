-- can be applied on archive running archive 5.27
create table key_value
(
    pk numeric(18,0) not null,
    content_type varchar(255) not null,
    created_time timestamp not null,
    key varchar(255) not null,
    updated_time timestamp not null,
    username varchar(255),
    value varchar(4000) not null,
    primary key (pk)
);

alter table key_value
    add constraint UK_gvyp924rq0a5y8u3emjs35g2q unique (key);

create index UK_o92kvbnsf2cnttpubgfpd6p01 on key_value (username);
create index UK_51ia14mc4pabswlvaqnt43clb on key_value (updated_time);

create generator key_value_pk_seq;
-- part 2: shall be applied on stopped archive before starting 5.27

-- part 3: can be applied on already running archive 5.27
