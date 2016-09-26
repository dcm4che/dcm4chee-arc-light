create index UK_6ry2squ4qcv129lxpae1oy93m on study (created_time);

create table ext_retrieve_aet (pk int8 not null, retrieve_aet varchar(255) not null, instance_fk int8, primary key (pk));
alter table ext_retrieve_aet add constraint FK_h5738j9g4vrxxh0n06v74f9pq foreign key (instance_fk) references instance;
create index FK_h5738j9g4vrxxh0n06v74f9pq on ext_retrieve_aet (instance_fk) ;

create table study_ext_retrieve_aet (pk int8 not null, retrieve_aet varchar(255) not null, study_fk int8, primary key (pk));
alter table study_ext_retrieve_aet add constraint FK_4ewtjnrk7hiy1dhypmvxrmeyc foreign key (study_fk) references study;
create index FK_4ewtjnrk7hiy1dhypmvxrmeyc on study_ext_retrieve_aet (study_fk) ;
create index UK_9bjf166lbmyirre66uoy96wag on study_ext_retrieve_aet (retrieve_aet);

create table stgcmt_result (pk int8 not null, created_time timestamp not null, device_name varchar(255) not null, exporter_id varchar(255) not null, num_failures int4, num_instances int4, series_iuid varchar(255), sop_iuid varchar(255), stgcmt_status int4 not null, study_iuid varchar(255) not null, transaction_uid varchar(255) not null, updated_time timestamp not null, primary key (pk));
alter table stgcmt_result add constraint UK_ey6qpep2qtiwayou7pd0vj22w  unique (transaction_uid);
create index UK_qko59fn9pb87j1eu070ilfkhm on stgcmt_result (updated_time);
create index UK_ey6qpep2qtiwayou7pd0vj22w on stgcmt_result (transaction_uid);
create index UK_7ltjgxoijy15rrwihl8euv7vh on stgcmt_result (device_name);
create index UK_gu96kxnbf2p84d1katepo0btq on stgcmt_result (exporter_id);
create index UK_p65blcj4h0uh2itb0bp49mc07 on stgcmt_result (study_iuid);
create index UK_nyoefler7agcmxc8t8yfngq7e on stgcmt_result (stgcmt_status);

alter table instance add num_frames int4;

create sequence ext_retrieve_aet_pk_seq;
create sequence stgcmt_result_pk_seq;
create sequence study_ext_retrieve_aet_pk_seq;
