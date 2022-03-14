-- can be applied on archive running archive 5.25
alter table series
    add receiving_hl7_app       varchar(255);
alter table series
    add receiving_hl7_facility      varchar(255);
alter table series
    add sending_hl7_app varchar(255);
alter table series
    add sending_hl7_facility varchar(255);

alter table mpps
    add accno_entity_id       varchar(255);
alter table mpps
    add accno_entity_uid      varchar(255);
alter table mpps
    add accno_entity_uid_type varchar(255);

alter table mwl_item
    add accno_entity_id       varchar(255);
alter table mwl_item
    add accno_entity_uid      varchar(255);
alter table mwl_item
    add accno_entity_uid_type varchar(255);
alter table mwl_item
    add admid_entity_id       varchar(255);
alter table mwl_item
    add admid_entity_uid      varchar(255);
alter table mwl_item
    add admid_entity_uid_type varchar(255);

alter table patient_id
    add entity_id       varchar(255);
alter table patient_id
    add entity_uid      varchar(255);
alter table patient_id
    add entity_uid_type varchar(255);

alter table series_req
    add accno_entity_id       varchar(255);
alter table series_req
    add accno_entity_uid      varchar(255);
alter table series_req
    add accno_entity_uid_type varchar(255);

alter table study
    add accno_entity_id       varchar(255);
alter table study
    add accno_entity_uid      varchar(255);
alter table study
    add accno_entity_uid_type varchar(255);
alter table study
    add admid_entity_id       varchar(255);
alter table study
    add admid_entity_uid      varchar(255);
alter table study
    add admid_entity_uid_type varchar(255);

alter table ups
    add admid_entity_id       varchar(255);
alter table ups
    add admid_entity_uid      varchar(255);
alter table ups
    add admid_entity_uid_type varchar(255);

alter table ups_req
    add accno_entity_id       varchar(255);
alter table ups_req
    add accno_entity_uid      varchar(255);
alter table ups_req
    add accno_entity_uid_type varchar(255);

create table rel_ups_station_class_code (ups_fk bigint not null, station_class_code_fk bigint not null);
create table rel_ups_station_location_code (ups_fk bigint not null, station_location_code_fk bigint not null);
create table rel_ups_station_name_code (ups_fk bigint not null, station_name_code_fk bigint not null);

alter table rel_ups_station_class_code
    add constraint FK_q26e06qk9gwviwe2ug0f86doa foreign key (station_class_code_fk) references code;
alter table rel_ups_station_class_code
    add constraint FK_e1ioaswm010jlsq6kl7y3um1c foreign key (ups_fk) references ups;

alter table rel_ups_station_location_code
    add constraint FK_kl60ab0k5c1p8qii9ya16424x foreign key (station_location_code_fk) references code;
alter table rel_ups_station_location_code
    add constraint FK_9f0l4glqwpq12d11w9osd475m foreign key (ups_fk) references ups;

alter table rel_ups_station_name_code
    add constraint FK_jtv4r8f88f6gfte0fa36w5y9o foreign key (station_name_code_fk) references code;
alter table rel_ups_station_name_code
    add constraint FK_8jf5xe8ot2yammv3ksd5xrgif foreign key (ups_fk) references ups;

alter table hl7psu_task
    add pps_status int;
alter table hl7psu_task
    drop constraint UK_p5fraoqdbaywmlyumaeo16t56;
alter table hl7psu_task
    add constraint UK_1t3jge4o2fl1byp3y8ljmkb3m  unique (study_iuid, pps_status);

update patient_id
    set entity_id = (select issuer.entity_id from issuer where issuer_fk = issuer.pk)
where issuer_fk is not null;
update patient_id
    set entity_uid = (select issuer.entity_uid from issuer where issuer_fk = issuer.pk)
where issuer_fk is not null;
update patient_id
    set entity_uid_type = (select issuer.entity_uid_type from issuer where issuer_fk = issuer.pk)
where issuer_fk is not null;

create index UK_ffpftwfkijejj09tlbxr7u5g8 on series (sending_hl7_app);
create index UK_1e4aqxc5w1557hr3fb3lqm2qb on series (sending_hl7_facility);
create index UK_gj0bxgi55bhjic9s3i4dp2aee on series (receiving_hl7_app);
create index UK_pbay159cdhwbtjvlmel6d6em2 on series (receiving_hl7_facility);

create index UK_tkyjkkxxhnr0fem7m0h3844jk on patient_id (pat_id);
create index UK_d1sdyupb0vwvx23jownjnyy72 on patient_id (entity_id);
create index UK_m2jq6xe87vegohf6g10t5ptew on patient_id (entity_uid, entity_uid_type);

create index FK_q26e06qk9gwviwe2ug0f86doa on rel_ups_station_class_code (station_class_code_fk) ;
create index FK_e1ioaswm010jlsq6kl7y3um1c on rel_ups_station_class_code (ups_fk) ;
create index FK_kl60ab0k5c1p8qii9ya16424x on rel_ups_station_location_code (station_location_code_fk) ;
create index FK_9f0l4glqwpq12d11w9osd475m on rel_ups_station_location_code (ups_fk) ;
create index FK_jtv4r8f88f6gfte0fa36w5y9o on rel_ups_station_name_code (station_name_code_fk) ;
create index FK_8jf5xe8ot2yammv3ksd5xrgif on rel_ups_station_name_code (ups_fk) ;

-- part 2: shall be applied on stopped archive before starting 5.25
update mpps
    set accno_entity_id = (select issuer.entity_id from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null;
update mpps
    set accno_entity_uid = (select issuer.entity_uid from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null;
update mpps
    set accno_entity_uid_type = (select issuer.entity_uid_type from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null;

update mwl_item
    set accno_entity_id = (select issuer.entity_id from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null;
update mwl_item
    set accno_entity_uid = (select issuer.entity_uid from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null;
update mwl_item
    set accno_entity_uid_type = (select issuer.entity_uid_type from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null;

update mwl_item
    set admid_entity_id = (select issuer.entity_id from issuer where admid_issuer_fk = issuer.pk)
where admid_issuer_fk is not null;
update mwl_item
    set admid_entity_uid = (select issuer.entity_uid from issuer where admid_issuer_fk = issuer.pk)
where admid_issuer_fk is not null;
update mwl_item
    set admid_entity_uid_type = (select issuer.entity_uid_type from issuer where admid_issuer_fk = issuer.pk)
where admid_issuer_fk is not null;

update series_req
    set accno_entity_id = (select issuer.entity_id from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null;
update series_req
    set accno_entity_uid = (select issuer.entity_uid from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null;
update series_req
    set accno_entity_uid_type = (select issuer.entity_uid_type from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null;

update study
    set accno_entity_id = (select issuer.entity_id from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null;
update study
    set accno_entity_uid = (select issuer.entity_uid from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null;
update study
    set accno_entity_uid_type = (select issuer.entity_uid_type from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null;

update study
    set admid_entity_id = (select issuer.entity_id from issuer where admid_issuer_fk = issuer.pk)
where admid_issuer_fk is not null;
update study
    set admid_entity_uid = (select issuer.entity_uid from issuer where admid_issuer_fk = issuer.pk)
where admid_issuer_fk is not null;
update study
    set admid_entity_uid_type = (select issuer.entity_uid_type from issuer where admid_issuer_fk = issuer.pk)
where admid_issuer_fk is not null;

update ups
    set admid_entity_id = (select issuer.entity_id from issuer where admission_issuer_fk = issuer.pk)
where admission_issuer_fk is not null;
update ups
    set admid_entity_uid = (select issuer.entity_uid from issuer where admission_issuer_fk = issuer.pk)
where admission_issuer_fk is not null;
update ups
    set admid_entity_uid_type = (select issuer.entity_uid_type from issuer where admission_issuer_fk = issuer.pk)
where admission_issuer_fk is not null;

update ups_req
    set accno_entity_id = (select issuer.entity_id from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null;
update ups_req
    set accno_entity_uid = (select issuer.entity_uid from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null;
update ups_req
    set accno_entity_uid_type = (select issuer.entity_uid_type from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null;

insert into rel_ups_station_name_code
    select ups.pk, ups.station_name_fk from ups where ups.station_name_fk is not null;

insert into rel_ups_station_class_code
    select ups.pk, ups.station_class_fk from ups where ups.station_class_fk is not null;

insert into rel_ups_station_location_code
    select ups.pk, ups.station_location_fk from ups where ups.station_location_fk is not null;

update mpps
    set accno_entity_id = (select issuer.entity_id from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null
  and accno_entity_id is null
  and accno_entity_uid is null;
update mpps
    set accno_entity_uid = (select issuer.entity_uid from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null
  and accno_entity_id is null
  and accno_entity_uid is null;
update mpps
    set accno_entity_uid_type = (select issuer.entity_uid_type from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null
  and accno_entity_id is null
  and accno_entity_uid is null;

update mwl_item
    set accno_entity_id = (select issuer.entity_id from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null
  and accno_entity_id is null
  and accno_entity_uid is null;
update mwl_item
    set accno_entity_uid = (select issuer.entity_uid from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null
  and accno_entity_id is null
  and accno_entity_uid is null;
update mwl_item
    set accno_entity_uid_type = (select issuer.entity_uid_type from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null
  and accno_entity_id is null
  and accno_entity_uid is null;

update mwl_item
    set admid_entity_id = (select issuer.entity_id from issuer where admid_issuer_fk = issuer.pk)
where admid_issuer_fk is not null
  and admid_entity_id is null
  and admid_entity_uid is null;
update mwl_item
    set admid_entity_uid = (select issuer.entity_uid from issuer where admid_issuer_fk = issuer.pk)
where admid_issuer_fk is not null
  and admid_entity_id is null
  and admid_entity_uid is null;
update mwl_item
    set admid_entity_uid_type = (select issuer.entity_uid_type from issuer where admid_issuer_fk = issuer.pk)
where admid_issuer_fk is not null
  and admid_entity_id is null
  and admid_entity_uid is null;

update patient_id
    set entity_id = (select issuer.entity_id from issuer where issuer_fk = issuer.pk)
where issuer_fk is not null
  and entity_id is null
  and entity_uid is null;
update patient_id
    set entity_uid = (select issuer.entity_uid from issuer where issuer_fk = issuer.pk)
where issuer_fk is not null
  and entity_id is null
  and entity_uid is null;
update patient_id
    set entity_uid_type = (select issuer.entity_uid_type from issuer where issuer_fk = issuer.pk)
where issuer_fk is not null
  and entity_id is null
  and entity_uid is null;

update series_req
    set accno_entity_id = (select issuer.entity_id from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null
  and accno_entity_id is null
  and accno_entity_uid is null;
update series_req
    set accno_entity_uid = (select issuer.entity_uid from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null
  and accno_entity_id is null
  and accno_entity_uid is null;
update series_req
    set accno_entity_uid_type = (select issuer.entity_uid_type from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null
  and accno_entity_id is null
  and accno_entity_uid is null;

update study
    set accno_entity_id = (select issuer.entity_id from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null
  and accno_entity_id is null
  and accno_entity_uid is null;
update study
    set accno_entity_uid = (select issuer.entity_uid from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null
  and accno_entity_id is null
  and accno_entity_uid is null;
update study
    set accno_entity_uid_type = (select issuer.entity_uid_type from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null
  and accno_entity_id is null
  and accno_entity_uid is null;

update study
    set admid_entity_id = (select issuer.entity_id from issuer where admid_issuer_fk = issuer.pk)
where admid_issuer_fk is not null
  and admid_entity_id is null
  and admid_entity_uid is null;
update study
    set admid_entity_uid = (select issuer.entity_uid from issuer where admid_issuer_fk = issuer.pk)
where admid_issuer_fk is not null
  and admid_entity_id is null
  and admid_entity_uid is null;
update study
    set admid_entity_uid_type = (select issuer.entity_uid_type from issuer where admid_issuer_fk = issuer.pk)
where admid_issuer_fk is not null
  and admid_entity_id is null
  and admid_entity_uid is null;

update ups
    set admid_entity_id = (select issuer.entity_id from issuer where admission_issuer_fk = issuer.pk)
where admission_issuer_fk is not null
  and admid_entity_id is null
  and admid_entity_uid is null;
update ups
    set admid_entity_uid = (select issuer.entity_uid from issuer where admission_issuer_fk = issuer.pk)
where admission_issuer_fk is not null
  and admid_entity_id is null
  and admid_entity_uid is null;
update ups
    set admid_entity_uid_type = (select issuer.entity_uid_type from issuer where admission_issuer_fk = issuer.pk)
where admission_issuer_fk is not null
  and admid_entity_id is null
  and admid_entity_uid is null;

update ups_req
    set accno_entity_id = (select issuer.entity_id from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null
  and accno_entity_id is null
  and accno_entity_uid is null;
update ups_req
    set accno_entity_uid = (select issuer.entity_uid from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null
  and accno_entity_id is null
  and accno_entity_uid is null;
update ups_req
    set accno_entity_uid_type = (select issuer.entity_uid_type from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null
  and accno_entity_id is null
  and accno_entity_uid is null;

-- part 3: can be applied on already running archive 5.25
drop index FK_grl2idmms10qq4lhmh909jxtj on mpps;
alter table mpps
    drop constraint FK_grl2idmms10qq4lhmh909jxtj;
alter table mpps
    drop column accno_issuer_fk;

drop index FK_ot32lpvialton54xqh636c4it on mwl_item;
drop index FK_9k8x73a91nd9q7ux7h5itkyh5 on mwl_item;
alter table mwl_item
    drop constraint FK_ot32lpvialton54xqh636c4it;
alter table mwl_item
    drop constraint FK_9k8x73a91nd9q7ux7h5itkyh5;
alter table mwl_item
    drop column accno_issuer_fk;
alter table mwl_item
    drop column admid_issuer_fk;

drop index UK_31gvi9falc03xs94m8l3pgoid on patient_id;
drop index FK_oo232lt89k1b5h8mberi9v152 on patient_id;
alter table patient_id
    drop constraint FK_oo232lt89k1b5h8mberi9v152;
alter table patient_id
    drop column issuer_fk;

drop index FK_se4n39as61wwf92ggbfc9yglo on series_req;
alter table series_req
    drop constraint FK_se4n39as61wwf92ggbfc9yglo;
alter table series_req
    drop column accno_issuer_fk;

drop index FK_lp0rdx659kewq8qrqg702yfyv on study;
alter table study
    drop constraint FK_lp0rdx659kewq8qrqg702yfyv;
alter table study
    drop column accno_issuer_fk;
drop index FK_9fqno60wc3gr4376ov1xlfme4 on study;
alter table study
    drop constraint FK_9fqno60wc3gr4376ov1xlfme4;
alter table study
    drop column admid_issuer_fk;

drop index FK_61tpdp9aoy98jwiif5wq82ia3 on ups;
alter table ups
    drop constraint FK_61tpdp9aoy98jwiif5wq82ia3;
alter table ups
    drop column admission_issuer_fk;

drop index FK_gd2hu9idxg6rd71g1i8r8wyjr on ups;
alter table ups
    drop constraint FK_gd2hu9idxg6rd71g1i8r8wyjr;
alter table ups
    drop column station_name_fk;

drop index FK_ak183xmw0sai4jg9lib6m14o2 on ups;
alter table ups
    drop constraint FK_ak183xmw0sai4jg9lib6m14o2;
alter table ups
    drop column station_class_fk;

drop index FK_ox3hpmd042ywnww3yh33crcoj on ups;
alter table ups
    drop constraint FK_ox3hpmd042ywnww3yh33crcoj;
alter table ups
    drop column station_location_fk;

drop index FK_gegm1c1ymem7tj2wcm0o7e0pu on ups_req;
alter table ups_req
    drop constraint FK_gegm1c1ymem7tj2wcm0o7e0pu;
alter table ups_req
    drop column accno_issuer_fk;

drop index UK_t1p7jajas0mu12sx8jvtp2y0f on issuer;
drop index UK_gknfxd1vh283cmbg8ymia9ms8 on issuer;
drop table issuer;

drop sequence issuer_pk_seq;