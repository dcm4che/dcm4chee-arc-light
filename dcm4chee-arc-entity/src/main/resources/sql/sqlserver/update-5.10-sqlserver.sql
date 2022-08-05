update study set ext_retrieve_aet = '*' where ext_retrieve_aet is null;
drop index UK_cl9dmi0kb97ov1cjh7rn3dhve on study;
alter table study alter column ext_retrieve_aet VARCHAR(255) not null;
create index UK_cl9dmi0kb97ov1cjh7rn3dhve on study (ext_retrieve_aet);

alter table study add completeness int;
update study set completeness = 2;
update study set completeness = 1 where failed_retrieves > 0 and failed_iuids is not null;
update study set completeness = 0 where failed_retrieves = 0 and failed_iuids = '*';
alter table study alter column completeness int not null;
drop INDEX UK_btfu9p1kwhrr444muytvxguci on study;
alter table study drop column failed_iuids;
create index UK_gl5rq54a0tr8nreu27c2t04rb on study (completeness);

alter table series add completeness int;
update series set completeness = 2;
update series set completeness = 1 where failed_retrieves > 0 and failed_iuids is not null;
update series set completeness = 0 where failed_retrieves = 0 and failed_iuids = '*';
alter table series alter column completeness int not null;
drop index UK_twtj9t0jbl07buyisdtvqrpy on series;
alter table series drop column failed_iuids;
create index UK_4lnegvfs65fbkjn7nmg9s8usy on series (completeness);

drop index UK_b5mbe6jenklf1r5wp5csrvf67 on queue_msg;
alter table queue_msg alter column queue_name varchar(255) not null;
create index UK_b5mbe6jenklf1r5wp5csrvf67 on queue_msg (queue_name);
alter table export_task add created_time datetime2 not null, updated_time datetime2 not null, num_instances int, modalities varchar(255), queue_msg_fk bigint;
update export_task set created_time = current_timestamp, updated_time = current_timestamp;

alter table export_task drop constraint UK_aoqbyfnen6evu73ltc1osexfr;
alter table export_task add constraint FK_g6atpiywpo2100kn6ovix7uet foreign key (queue_msg_fk) references queue_msg;

create index UK_c5cof80jx0oopvovf3p4jv4l8 on export_task (device_name);
create index UK_p5jjs08sdp9oecvr93r2g0kyq on export_task (updated_time);
create index UK_j1t0mj3vlmf5xwt4fs5xida1r on export_task (scheduled_time);
create index UK_q7gmfr3aog1hateydhfeiu7si on export_task (exporter_id);
create index UK_hb9rftf7opmg56nkg7dkvsdc8 on export_task (study_iuid, series_iuid, sop_iuid);
create index FK_g6atpiywpo2100kn6ovix7uet on export_task (queue_msg_fk);
