-- can be applied on archive running archive 5.27
create table key_value
(
    pk           int8          not null,
    content_type varchar(255)  not null,
    created_time timestamp     not null,
    key          varchar(255)  not null,
    updated_time timestamp     not null,
    user         varchar(255)  not null,
    value        varchar(4000) not null,
    primary key (pk)
);

alter table key_value
    add constraint UK_pbdnrclnbwmbsbruwe0avqbh unique (key, user);

create index UK_51ia14mc4pabswlvaqnt43clb on key_value (updated_time);

create sequence key_value_pk_seq;

-- part 2: shall be applied on stopped archive before starting 5.27

-- part 3: can be applied on already running archive 5.27
