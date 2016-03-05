alter table study drop access_control_id;
alter table study add access_control_id varchar(255) not null, add access_time timestamp not null, add scattered_storage smallint not null;
update study set access_time = updated_time, scattered_storage = false, access_control_id = '*';
create index UK_q8k2sl3kjl18qg1nr19l47tl1 on study (access_time);
create index UK_24av2ewa70e7cykl340n63aqd on study (access_control_id);
alter table code drop constraint UK_sb4oc9lkns36wswku831c33w6;
alter table code add constraint UK_l01jou0o1rohy7a9p933ndrxg  unique (code_value, code_designator);
alter table study add failed_retrieves integer not null, add failed_iuids varchar(4000);
update study set failed_retrieves = 0;
