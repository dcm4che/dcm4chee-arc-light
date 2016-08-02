alter table patient add num_studies int;
update patient set num_studies = (
  select count(*) from study where study.patient_fk=patient.pk and study.rejection_state in (0,1));
alter table location add multi_ref int, add uidmap_fk bigint, add object_type int;
update location set object_type = 0;
alter table location alter column object_type set not null;
alter table location alter column tsuid drop not null;
create table uidmap (pk bigint identity not null, uidmap image not null, primary key (pk));

--to be checked---
alter table instance add inst_no_int int;
update instance set inst_no_int = inst_no::int where inst_no != '*';
update instance set inst_no_int = null where inst_no = '*';
alter table instance drop inst_no;
alter table instance rename column inst_no_int to inst_no;
alter table series add series_no_int int;
update series set series_no_int = series_no::int where series_no != '*';
update series set series_no_int = null where series_no = '*';
alter table series drop series_no;
alter table series rename column series_no_int to series_no;
--to be checked---

create index UK_i1lnahmehau3r3j9pdyxg3p3y on location (multi_ref);
alter table location add constraint FK_bfk5vl6eoxaf0hhwiu3rbgmkn foreign key (uidmap_fk) references uidmap;

--to be checked--
create index FK_bfk5vl6eoxaf0hhwiu3rbgmkn on location (uidmap_fk) ;
create sequence uidmap_pk_seq;
--to be checked--