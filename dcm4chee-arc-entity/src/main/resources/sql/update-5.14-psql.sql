create table stgcmt_task (pk int8 not null, completed int4 not null, created_time timestamp not null, failed int4 not null, local_aet varchar(255) not null, series_iuid varchar(255), sop_iuid varchar(255), stgcmt_policy int4, storage_ids varchar(255), study_iuid varchar(255) not null, update_location_status boolean, updated_time timestamp not null, queue_msg_fk int8 not null, primary key (pk));
alter table stgcmt_task add constraint UK_ichfp1g5sfs44cawed96slc7x  unique (queue_msg_fk);
create index UK_ejrov8eocy9p1mql24phufiuh on stgcmt_task (created_time);
create index UK_621cqo3dgwudxafm0h4idugtm on stgcmt_task (updated_time);
create index UK_s50atqx11ivtf4bihi6e2v7ck on stgcmt_task (study_iuid, series_iuid, sop_iuid);
alter table stgcmt_task add constraint FK_ichfp1g5sfs44cawed96slc7x foreign key (queue_msg_fk) references queue_msg;

create index FK_ichfp1g5sfs44cawed96slc7x on stgcmt_task (queue_msg_fk) ;

create sequence stgcmt_task_pk_seq;
