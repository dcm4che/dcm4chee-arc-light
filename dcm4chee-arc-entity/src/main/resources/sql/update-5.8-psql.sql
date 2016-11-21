alter table series add metadata_update_time timestamp, add metadata_storage_id varchar(255), add metadata_storage_path varchar(255);

create index UK_hwkcpd7yv0nca7o918wm4bn69 on series (metadata_update_time);
