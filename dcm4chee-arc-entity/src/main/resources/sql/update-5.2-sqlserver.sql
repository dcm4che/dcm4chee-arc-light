alter table queue_msg alter column msg_props varchar(4000) not null;
alter table location alter column object_size bigint not null;
alter table location alter column storage_id varchar(255) not null;
alter table location alter column storage_path varchar(255) not null;
alter table location alter column tsuid varchar(255) not null;
create table ian_task (pk bigint identity not null, calling_aet varchar(255) not null, device_name varchar(255) not null, ian_dests varchar(255) not null, scheduled_time datetime2, study_iuid varchar(255), mpps_fk bigint, primary key (pk));
alter table ian_task add constraint UK_dq88edcjjxh7h92f89y5ueast  unique (study_iuid);
create index UK_5shiir23exao1xpy2n5gvasrh on ian_task (device_name);
alter table ian_task add constraint FK_1fuh251le2hid2byw90hd1mly foreign key (mpps_fk) references mpps;
update code set code_version = '*' where code_version is null;
alter table code alter column code_version varchar(255) not null;
alter table code drop constraint UK_l01jou0o1rohy7a9p933ndrxg;
alter table code add constraint UK_sb4oc9lkns36wswku831c33w6  unique (code_value, code_designator, code_version);
alter table study drop scattered_storage;   --uncertain
alter table study add storage_ids varchar(255);

----uncertain-start-----
update study set storage_ids = (
  select string_agg(distinct cast(storage_id as varchar), '\')
  from location
    join instance on location.instance_fk = instance.pk
    join series on instance.series_fk = series.pk
  where study_fk = study.pk);
----uncertain-end-------

alter table study alter column storage_ids varchar(255) not null;
create index UK_fypbtohf5skbd3bkyd792a6dt on study (storage_ids);
alter table series add rejection_state int;
update series set rejection_state = 1;

----uncertain-start-----
update series set rejection_state = 0 where not exists (
  select 1 from instance where series.pk = instance.series_fk and instance.reject_code_fk is not null);
update series set rejection_state = 2 where rejection_state = 1 and not exists (
  select 1 from instance where series.pk = instance.series_fk and instance.reject_code_fk is null);
----uncertain-end-------

alter table series alter column rejection_state int not null;
create index UK_jlgy9ifvqak4g2bxkchismw8x on series (rejection_state);
alter table study add rejection_state int;
update study set rejection_state = 1;

----uncertain-start-----
update study set rejection_state = 0 where not exists (
  select 1 from series where study.pk = series.study_fk and series.rejection_state != 0);
update study set rejection_state = 2 where not exists (
  select 1 from series where study.pk = series.study_fk and series.rejection_state != 2);
----uncertain-end-------

alter table study alter column rejection_state int not null;
create index UK_hwu9omd369ju3nufufxd3vof2 on study (rejection_state);