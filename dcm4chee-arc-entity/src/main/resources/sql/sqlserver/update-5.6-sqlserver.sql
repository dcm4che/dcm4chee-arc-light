alter table patient add num_studies int;
update patient set num_studies = (
    select count(*) from study where study.patient_fk=patient.pk and study.rejection_state in (0,1));
alter table patient alter column num_studies int not null;
create index UK_296rccryifu6d8byisl2f4dvq on patient (num_studies);

alter table location add multi_ref int;
alter table location add uidmap_fk bigint;
alter table location add object_type int;
update location set object_type = 0;
alter table location alter column object_type int not null;
alter table location alter column tsuid varchar(255) null;
create table uidmap (pk bigint identity not null, uidmap image not null, primary key (pk));

alter table instance add inst_no_int int;
update instance set inst_no_int = inst_no where inst_no != '*';
update instance set inst_no_int = null where inst_no = '*';
drop index UK_ouh6caecancvsa05lknojy30j on instance;
alter table instance drop column inst_no;
exec sp_rename 'instance.inst_no_int', inst_no, 'COLUMN';

alter table series add series_no_int int;
update series set series_no_int = series_no where series_no != '*';
update series set series_no_int = null where series_no = '*';
drop index UK_75oc6w5ootkuwyvmrhe3tbown on series;
alter table series drop column series_no;
exec sp_rename 'series.series_no_int', series_no, 'COLUMN';

create index UK_i1lnahmehau3r3j9pdyxg3p3y on location (multi_ref);
alter table location add constraint FK_bfk5vl6eoxaf0hhwiu3rbgmkn foreign key (uidmap_fk) references uidmap;

create index UK_j6aadbh7u93bpmv18s1inrl1r on series (failed_retrieves);
create index UK_9qvng5j8xnli8yif7p0rjngb2 on study (failed_retrieves);

create index UK_twtj9t0jbl07buyisdtvqrpy on series (failed_iuids);
create index UK_btfu9p1kwhrr444muytvxguci on study (failed_iuids);