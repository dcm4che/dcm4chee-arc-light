alter table queue_msg alter msg_props type varchar(4000);
alter table location alter object_size set not null, alter storage_id set not null, alter storage_path set not null, alter tsuid set not null;
create table ian_task (pk int8 not null, calling_aet varchar(255) not null, device_name varchar(255) not null, ian_dests varchar(255) not null, scheduled_time timestamp, study_iuid varchar(255), mpps_fk int8, primary key (pk));
alter table ian_task add constraint UK_dq88edcjjxh7h92f89y5ueast  unique (study_iuid);
create index UK_5shiir23exao1xpy2n5gvasrh on ian_task (device_name);
alter table ian_task add constraint FK_1fuh251le2hid2byw90hd1mly foreign key (mpps_fk) references mpps;
create sequence ian_task_pk_seq;
