alter table patient add num_studies number(10,0);
update patient set num_studies = (
  select count(*) from study where study.patient_fk=patient.pk and study.rejection_state in (0,1));
alter table location add ( multi_ref number(10,0), uidmap_fk number(19,0), object_type number(10,0));
update location set object_type = 0;
alter table location modify object_type not null;
alter table location modify tsuid null;
create table uidmap (pk number(19,0) not null, uidmap blob not null, primary key (pk));
create index UK_i1lnahmehau3r3j9pdyxg3p3y on location (multi_ref);
alter table location add constraint FK_bfk5vl6eoxaf0hhwiu3rbgmkn foreign key (uidmap_fk) references uidmap;
create index FK_bfk5vl6eoxaf0hhwiu3rbgmkn on location (uidmap_fk) ;
create sequence uidmap_pk_seq;
