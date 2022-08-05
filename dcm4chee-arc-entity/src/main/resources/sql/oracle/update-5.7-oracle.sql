alter table patient add resp_person_fk number(19,0);
alter table patient add constraint FK_56r2g5ggptqgcvb3hl11adke2 foreign key (resp_person_fk) references person_name;
create index FK_56r2g5ggptqgcvb3hl11adke2 on patient (resp_person_fk) ;

alter table study modify storage_ids null;
alter table study add ext_retrieve_aet varchar2(255);

alter table series add ext_retrieve_aet varchar2(255);
alter table series modify series_no null;

alter table instance add (ext_retrieve_aet varchar2(255), num_frames number(10,0));
alter table instance modify inst_no null;

create index UK_cl9dmi0kb97ov1cjh7rn3dhve on study (ext_retrieve_aet);
create index UK_6ry2squ4qcv129lxpae1oy93m on study (created_time);
drop index UK_3tvtv5bjrpem0qjc3qo84bgsl;

create table stgcmt_result (pk number(19,0) not null, created_time timestamp not null, device_name varchar2(255 char) not null, exporter_id varchar2(255 char) not null, num_failures number(10,0), num_instances number(10,0), series_iuid varchar2(255 char), sop_iuid varchar2(255 char), stgcmt_status number(10,0) not null, study_iuid varchar2(255 char) not null, transaction_uid varchar2(255 char) not null, updated_time timestamp not null, primary key (pk));
alter table stgcmt_result add constraint UK_ey6qpep2qtiwayou7pd0vj22w  unique (transaction_uid);
create index UK_qko59fn9pb87j1eu070ilfkhm on stgcmt_result (updated_time);
create index UK_7ltjgxoijy15rrwihl8euv7vh on stgcmt_result (device_name);
create index UK_gu96kxnbf2p84d1katepo0btq on stgcmt_result (exporter_id);
create index UK_p65blcj4h0uh2itb0bp49mc07 on stgcmt_result (study_iuid);
create index UK_nyoefler7agcmxc8t8yfngq7e on stgcmt_result (stgcmt_status);

alter table sps_station_aet drop column pk;
drop sequence sps_station_aet_pk_seq;

create sequence stgcmt_result_pk_seq;
