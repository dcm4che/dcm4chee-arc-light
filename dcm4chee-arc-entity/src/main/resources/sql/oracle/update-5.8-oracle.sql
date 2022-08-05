alter table study add modified_time timestamp;
update study set modified_time = updated_time;
alter table study modify modified_time not null;

create table metadata (pk number(19,0) not null, digest varchar2(255), object_size number(19,0) not null, status number(10,0) not null, storage_id varchar2(255) not null, storage_path varchar2(255) not null, primary key (pk));

alter table series add metadata_update_time timestamp;
alter table series add metadata_fk number(19,0);
alter table series add inst_purge_time timestamp;
alter table series add inst_purge_state number(10,0);
update series set inst_purge_state = 0;
alter table series modify inst_purge_state not null;
alter table series add constraint FK_pu4p7k1o9hleuk9rmxvw2ybj6 foreign key (metadata_fk) references metadata;

delete from series_query_attrs;
alter table series_query_attrs add cuids_in_series varchar2(255);

create index UK_f7c9hmq8pfypohkgkp5vkbhxp on metadata (storage_id, status);
create index UK_hwkcpd7yv0nca7o918wm4bn69 on series (metadata_update_time);
create index UK_a8vyikwd972jomyb3f6brcfh5 on series (inst_purge_time);
create index UK_er4ife08f6eaki91gt3hxt5e on series (inst_purge_state);
create index FK_pu4p7k1o9hleuk9rmxvw2ybj6 on series (metadata_fk) ;

create sequence metadata_pk_seq;