alter table study add access_time timestamp, add scattered_storage boolean;
update study set access_time = updated_time, scattered_storage = false;
alter table study alter access_time set not null, alter scattered_storage set not null;
create index UK_q8k2sl3kjl18qg1nr19l47tl1 on study (access_time);
