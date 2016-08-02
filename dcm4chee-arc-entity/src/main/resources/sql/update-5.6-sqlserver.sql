alter table patient add num_studies int;
update patient set num_studies = (
  select count(*) from study where study.patient_fk=patient.pk and study.rejection_state in (0,1));
alter table location add multi_ref int, add uidmap_fk bigint, add object_type int;
update location set object_type = 0;
alter table location alter column object_type set not null;
alter table location alter column tsuid drop not null;
create table uidmap (pk bigint identity not null, uidmap image not null, primary key (pk));
create index UK_i1lnahmehau3r3j9pdyxg3p3y on location (multi_ref);
alter table location add constraint FK_bfk5vl6eoxaf0hhwiu3rbgmkn foreign key (uidmap_fk) references uidmap;

--to be checked--
create index FK_bfk5vl6eoxaf0hhwiu3rbgmkn on location (uidmap_fk) ;
create sequence uidmap_pk_seq;
--to be checked--