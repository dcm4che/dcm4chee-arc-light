alter table queue_msg alter msg_props type varchar(4000);
alter table location alter object_size set not null, alter storage_id set not null, alter storage_path set not null, alter tsuid set not null;
create table ian_mpps_task (pk int8 not null, device_name varchar(255) not null, ian_dests varchar(255) not null, mpps_fk int8 not null, primary key (pk));
create table ian_study_task (pk int8 not null, device_name varchar(255) not null, ian_dests varchar(255) not null, scheduled_time timestamp not null, study_iuid varchar(255) not null, version int8, primary key (pk));
create sequence ian_mpps_task_pk_seq;
create sequence ian_study_task_pk_seq;
