update study set ext_retrieve_aet = '*' where ext_retrieve_aet is null;
alter table study add ext_retrieve_aet1 varchar(255) not null;
update STUDY set EXT_RETRIEVE_AET1=EXT_RETRIEVE_AET;
drop index UK_cl9dmi0kb97ov1cjh7rn3dhve;
alter TABLE STUDY DROP ext_retrieve_aetNew;
alter TABLE STUDY alter COLUMN EXT_RETRIEVE_AET1 to EXT_RETRIEVE_AET;
create index UK_cl9dmi0kb97ov1cjh7rn3dhve on study (ext_retrieve_aet);

alter table study add completeness integer not null;
update study set completeness = 2;
update study set completeness = 1 where failed_retrieves > 0 and failed_iuids is not null;
update study set completeness = 0 where failed_retrieves = 0 and failed_iuids = '*';
drop index UK_BTFU9P1KWHRR444MUYTVXGUCI;
alter table study drop failed_iuids;
create index UK_gl5rq54a0tr8nreu27c2t04rb on study (completeness);

alter table series add completeness integer not null;
update series set completeness = 2;
update series set completeness = 1 where failed_retrieves > 0 and failed_iuids is not null;
update series set completeness = 0 where failed_retrieves = 0 and failed_iuids = '*';
drop index UK_TWTJ9T0JBL07BUYISDTVQRPY;
alter table series drop failed_iuids;
create index UK_4lnegvfs65fbkjn7nmg9s8usy on series (completeness);


update queue_msg set queue_name = '*' where queue_name is null;
alter table queue_msg add queue_name1 varchar(255) not null;
update queue_msg set queue_name1=queue_name;
drop index UK_b5mbe6jenklf1r5wp5csrvf67;
alter table QUEUE_MSG drop queue_name;
alter table QUEUE_MSG alter COLUMN queue_name1 to queue_name;
create index UK_b5mbe6jenklf1r5wp5csrvf67 on queue_msg (queue_name);


alter table export_task add created_time timestamp not null;
alter table export_task add updated_time timestamp not null;
alter table export_task add num_instances integer;
alter table export_task add modalities varchar(255);
alter table export_task add queue_msg_fk numeric(18,0);
update export_task set created_time = current_timestamp, updated_time = current_timestamp;


alter table export_task drop constraint UK_aoqbyfnen6evu73ltc1osexfr;
alter table export_task add constraint FK_g6atpiywpo2100kn6ovix7uet foreign key (queue_msg_fk) references queue_msg;

drop index UK_cxaqwh62doxvy1itpdi43c681;
create index UK_c5cof80jx0oopvovf3p4jv4l8 on export_task (device_name);
create index UK_p5jjs08sdp9oecvr93r2g0kyq on export_task (updated_time);
create index UK_j1t0mj3vlmf5xwt4fs5xida1r on export_task (scheduled_time);
create index UK_q7gmfr3aog1hateydhfeiu7si on export_task (exporter_id);
create index UK_hb9rftf7opmg56nkg7dkvsdc8 on export_task (study_iuid, series_iuid, sop_iuid);
