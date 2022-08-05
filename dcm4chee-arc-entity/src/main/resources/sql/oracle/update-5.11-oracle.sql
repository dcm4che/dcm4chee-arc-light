-- can be applied on running archive 5.10
create table retrieve_task (pk number(19,0) not null, completed number(10,0) not null, created_time timestamp not null, destination_aet varchar2(255 char) not null, device_name varchar2(255 char) not null, error_comment varchar2(255 char), failed number(10,0) not null, local_aet varchar2(255 char) not null, remaining number(10,0) not null, remote_aet varchar2(255 char) not null, series_iuid varchar2(255 char), sop_iuid varchar2(255 char), status_code number(10,0) not null, study_iuid varchar2(255 char) not null, updated_time timestamp not null, warning number(10,0) not null, queue_msg_fk number(19,0) not null, primary key (pk));
alter table retrieve_task add constraint UK_mxokt1gw5g1e7rc3ssotvuqix  unique (queue_msg_fk);
create index UK_djkqk3dls3xkru1n0c3p5rm3 on retrieve_task (device_name);
create index UK_a26s4yqy4rnpw7nniuyt7tkpo on retrieve_task (local_aet);
create index UK_3avjusmul00fc3yi1notyh16j on retrieve_task (remote_aet);
create index UK_jgaej0gm9appih04n09qto8yh on retrieve_task (destination_aet);
create index UK_gafcma0d5wwdjlq8jueqknlq0 on retrieve_task (study_iuid);
alter table retrieve_task add constraint FK_mxokt1gw5g1e7rc3ssotvuqix foreign key (queue_msg_fk) references queue_msg;
create sequence retrieve_task_pk_seq;

alter table study add study_size number(19,0);
alter table series add series_size number(19,0);
alter table queue_msg add priority number(10,0);

-- shall be applied on stopped archive before starting 5.11
update study set study_size = -1;
update series set series_size = -1;
update queue_msg set priority = 4;

alter table study modify study_size not null;
alter table series modify series_size not null;
alter table queue_msg modify priority not null;

create index UK_q7vxiaj1q6ojfxdq1g9jjxgqv on study (study_size);
