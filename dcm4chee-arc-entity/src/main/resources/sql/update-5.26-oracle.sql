-- can be applied on archive running archive 5.25
alter table series
    add receiving_hl7_app       varchar2(255);
alter table series
    add receiving_hl7_facility      varchar2(255);
alter table series
    add sending_hl7_app varchar2(255);
alter table series
    add sending_hl7_facility varchar2(255);

alter table mpps
    add accno_entity_id       varchar2(255);
alter table mpps
    add accno_entity_uid      varchar2(255);
alter table mpps
    add accno_entity_uid_type varchar2(255);

alter table mwl_item
    add accno_entity_id       varchar2(255);
alter table mwl_item
    add accno_entity_uid      varchar2(255);
alter table mwl_item
    add accno_entity_uid_type varchar2(255);
alter table mwl_item
    add admid_entity_id       varchar2(255);
alter table mwl_item
    add admid_entity_uid      varchar2(255);
alter table mwl_item
    add admid_entity_uid_type varchar2(255);

alter table patient_id
    add entity_id       varchar(64);
alter table patient_id
    add entity_uid      varchar(64);
alter table patient_id
    add entity_uid_type varchar(64);

alter table series_req
    add accno_entity_id       varchar2(255);
alter table series_req
    add accno_entity_uid      varchar2(255);
alter table series_req
    add accno_entity_uid_type varchar2(255);

alter table study
    add accno_entity_id       varchar2(255);
alter table study
    add accno_entity_uid      varchar2(255);
alter table study
    add accno_entity_uid_type varchar2(255);
alter table study
    add admid_entity_id       varchar2(255);
alter table study
    add admid_entity_uid      varchar2(255);
alter table study
    add admid_entity_uid_type varchar2(255);

alter table ups
    add admid_entity_id       varchar2(255);
alter table ups
    add admid_entity_uid      varchar2(255);
alter table ups
    add admid_entity_uid_type varchar2(255);

alter table ups_req
    add accno_entity_id       varchar2(255);
alter table ups_req
    add accno_entity_uid      varchar2(255);
alter table ups_req
    add accno_entity_uid_type varchar2(255);

create table rel_ups_station_class_code (ups_fk number(19,0) not null, station_class_code_fk number(19,0) not null);
create table rel_ups_station_location_code (ups_fk number(19,0) not null, station_location_code_fk number(19,0) not null);
create table rel_ups_station_name_code (ups_fk number(19,0) not null, station_name_code_fk number(19,0) not null);

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
    add pps_status number(10,0);
alter table hl7psu_task
    drop constraint UK_p5fraoqdbaywmlyumaeo16t56;
alter table hl7psu_task
    add constraint UK_1t3jge4o2fl1byp3y8ljmkb3m  unique (study_iuid, pps_status);

update patient_id
set (entity_id, entity_uid, entity_uid_type) =
        (select issuer.entity_id, issuer.entity_uid, issuer.entity_uid_type
         from issuer where issuer_fk = issuer.pk)
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
set (accno_entity_id, accno_entity_uid, accno_entity_uid_type) =
        (select issuer.entity_id, issuer.entity_uid, issuer.entity_uid_type
         from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null;

update mwl_item
set (accno_entity_id, accno_entity_uid, accno_entity_uid_type) =
        (select issuer.entity_id, issuer.entity_uid, issuer.entity_uid_type
         from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null;

update mwl_item
set (admid_entity_id, admid_entity_uid, admid_entity_uid_type) =
        (select issuer.entity_id, issuer.entity_uid, issuer.entity_uid_type
         from issuer where admid_issuer_fk = issuer.pk)
where admid_issuer_fk is not null;

update patient_id
set (entity_id, entity_uid, entity_uid_type) =
        (select issuer.entity_id, issuer.entity_uid, issuer.entity_uid_type
         from issuer where issuer_fk = issuer.pk)
where issuer_fk is not null
  and entity_id is null
  and entity_uid is null;

update series_req
set (accno_entity_id, accno_entity_uid, accno_entity_uid_type) =
        (select issuer.entity_id, issuer.entity_uid, issuer.entity_uid_type
         from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null;

update study
set (accno_entity_id, accno_entity_uid, accno_entity_uid_type) =
        (select issuer.entity_id, issuer.entity_uid, issuer.entity_uid_type
         from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null;

update study
set (admid_entity_id, admid_entity_uid, admid_entity_uid_type) =
        (select issuer.entity_id, issuer.entity_uid, issuer.entity_uid_type
         from issuer where admid_issuer_fk = issuer.pk)
where admid_issuer_fk is not null;

update ups
set (admid_entity_id, admid_entity_uid, admid_entity_uid_type) =
        (select issuer.entity_id, issuer.entity_uid, issuer.entity_uid_type
         from issuer where ups.admission_issuer_fk = issuer.pk)
where admission_issuer_fk is not null;

update ups_req
set (accno_entity_id, accno_entity_uid, accno_entity_uid_type) =
        (select issuer.entity_id, issuer.entity_uid, issuer.entity_uid_type
         from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null;

insert into rel_ups_station_name_code
select ups.pk, ups.station_name_fk from ups where ups.station_name_fk is not null;

insert into rel_ups_station_class_code
select ups.pk, ups.station_class_fk from ups where ups.station_class_fk is not null;

insert into rel_ups_station_location_code
select ups.pk, ups.station_location_fk from ups where ups.station_location_fk is not null;

update mpps
set (accno_entity_id, accno_entity_uid, accno_entity_uid_type) =
        (select issuer.entity_id, issuer.entity_uid, issuer.entity_uid_type
         from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null
  and accno_entity_id is null
  and accno_entity_uid is null;

update mwl_item
set (accno_entity_id, accno_entity_uid, accno_entity_uid_type) =
        (select issuer.entity_id, issuer.entity_uid, issuer.entity_uid_type
         from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null
  and accno_entity_id is null
  and accno_entity_uid is null;

update mwl_item
set (admid_entity_id, admid_entity_uid, admid_entity_uid_type) =
        (select issuer.entity_id, issuer.entity_uid, issuer.entity_uid_type
         from issuer where admid_issuer_fk = issuer.pk)
where admid_issuer_fk is not null
  and admid_entity_id is null
  and admid_entity_uid is null;

update series_req
set (accno_entity_id, accno_entity_uid, accno_entity_uid_type) =
        (select issuer.entity_id, issuer.entity_uid, issuer.entity_uid_type
         from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null
  and accno_entity_id is null
  and accno_entity_uid is null;

update study
set (accno_entity_id, accno_entity_uid, accno_entity_uid_type) =
        (select issuer.entity_id, issuer.entity_uid, issuer.entity_uid_type
         from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null
  and accno_entity_id is null
  and accno_entity_uid is null;

update study
set (admid_entity_id, admid_entity_uid, admid_entity_uid_type) =
        (select issuer.entity_id, issuer.entity_uid, issuer.entity_uid_type
         from issuer where admid_issuer_fk = issuer.pk)
where admid_issuer_fk is not null
  and admid_entity_id is null
  and admid_entity_uid is null;

update ups
set (admid_entity_id, admid_entity_uid, admid_entity_uid_type) =
        (select issuer.entity_id, issuer.entity_uid, issuer.entity_uid_type
         from issuer where ups.admission_issuer_fk = issuer.pk)
where admission_issuer_fk is not null
  and admid_entity_id is null
  and admid_entity_uid is null;

update ups_req
set (accno_entity_id, accno_entity_uid, accno_entity_uid_type) =
        (select issuer.entity_id, issuer.entity_uid, issuer.entity_uid_type
         from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null
  and accno_entity_id is null
  and accno_entity_uid is null;

-- part 3: can be applied on already running archive 5.25
alter table mpps
    drop column accno_issuer_fk;

alter table mwl_item
    drop column accno_issuer_fk;
alter table mwl_item
    drop column admid_issuer_fk;

alter table patient_id
    drop constraint UK_31gvi9falc03xs94m8l3pgoid;
alter table patient_id
    drop column issuer_fk;

alter table series_req
    drop column accno_issuer_fk;

alter table study
    drop column accno_issuer_fk;
alter table study
    drop column admid_issuer_fk;

alter table ups
    drop column admission_issuer_fk;
alter table ups
    drop column station_name_fk;
alter table ups
    drop column station_class_fk;
alter table ups
    drop column station_location_fk;

alter table ups_req
    drop column accno_issuer_fk;

drop table issuer;
drop sequence issuer_pk_seq;