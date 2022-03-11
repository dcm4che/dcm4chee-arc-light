-- can be applied on archive running archive 5.25
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
    add entity_id       varchar(64);
alter table patient_id
    add entity_uid      varchar(64);
alter table patient_id
    add entity_uid_type varchar(64);

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
    add pps_status integer;
drop index UK_p5fraoqdbaywmlyumaeo16t56;
create unique index UK_1t3jge4o2fl1byp3y8ljmkb3m on hl7psu_task (study_iuid, pps_status);

update patient_id
set (entity_id, entity_uid, entity_uid_type) =
        (select issuer.entity_id, issuer.entity_uid, issuer.entity_uid_type
         from issuer where issuer_fk = issuer.pk)
where issuer_fk is not null;

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
    drop accno_issuer_fk;

alter table mwl_item
    drop accno_issuer_fk;
alter table mwl_item
    drop admid_issuer_fk;

alter table patient_id
    drop issuer_fk;

alter table series_req
    drop accno_issuer_fk;

alter table study
    drop accno_issuer_fk;
alter table study
    drop admid_issuer_fk;

alter table ups
    drop admission_issuer_fk;
alter table ups
    drop station_name_fk;
alter table ups
    drop station_class_fk;
alter table ups
    drop station_location_fk;

alter table ups_req
    drop accno_issuer_fk;

drop table issuer;