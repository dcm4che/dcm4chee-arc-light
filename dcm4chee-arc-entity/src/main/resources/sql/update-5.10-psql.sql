update study set ext_retrieve_aet = '*' where ext_retrieve_aet is null;
alter table study alter ext_retrieve_aet set not null;

alter table export_task add created_time timestamp, add updated_time timestamp, add proc_start_time timestamp, add proc_end_time timestamp, add msg_status int4, add num_failures int4, add num_instances int4, add msg_id varchar(255), add modalities varchar(255), add error_msg varchar(255), add outcome_msg varchar(255);
update export_task set created_time = current_timestamp, updated_time = current_timestamp, msg_status = 6, num_failures = 0;
alter table export_task alter created_time set not null, alter updated_time set not null, alter msg_status set not null, alter num_failures set not null;

alter table export_task drop constraint UK_aoqbyfnen6evu73ltc1osexfr;
drop index UK_cxaqwh62doxvy1itpdi43c681;
create index UK_c5cof80jx0oopvovf3p4jv4l8 on export_task (device_name);
create index UK_p5jjs08sdp9oecvr93r2g0kyq on export_task (updated_time);
create index UK_j1t0mj3vlmf5xwt4fs5xida1r on export_task (scheduled_time);
create index UK_q7gmfr3aog1hateydhfeiu7si on export_task (exporter_id);
create index UK_hb9rftf7opmg56nkg7dkvsdc8 on export_task (study_iuid, series_iuid, sop_iuid);
create index UK_ik18n9rpnwd3addwne5ypqyf on export_task (msg_status);
