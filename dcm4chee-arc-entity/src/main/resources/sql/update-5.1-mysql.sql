alter table study add access_time datetime, add scattered_storage bit;
update study set access_time = updated_time, scattered_storage = false, access_control_id = '*';
alter table study modify access_time datetime not null, modify scattered_storage bit not null, modify access_control_id varchar(255) not null;
create index UK_q8k2sl3kjl18qg1nr19l47tl1 on study (access_time);
create index UK_24av2ewa70e7cykl340n63aqd on study (access_control_id);
