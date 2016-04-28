--not working-start--
alter table queue_msg alter msg_props varchar(4000) not null;
alter table location alter object_size set not null, alter storage_id set not null, alter storage_path set not null, alter tsuid set not null;
--not working-end--

create table ian_task (pk numeric(18,0) not null, calling_aet varchar(255) not null, device_name varchar(255) not null, ian_dests varchar(255) not null, scheduled_time timestamp, study_iuid varchar(255), mpps_fk numeric(18,0), primary key (pk));
alter table ian_task add constraint UK_dq88edcjjxh7h92f89y5ueast  unique (study_iuid);

--not working-start--
create index UK_5shiir23exao1xpy2n5gvasrh on ian_task (device_name);
--not working-end--

alter table ian_task add constraint FK_1fuh251le2hid2byw90hd1mly foreign key (mpps_fk) references mpps;

--not working-start--
create sequence ian_task_pk_seq;
--not working-end--

update code set code_version = '*' where code_version is null;

--not working-start--
alter table code alter code_version set not null;
--not working-end--

alter table code drop constraint UK_l01jou0o1rohy7a9p933ndrxg;
alter table code add constraint UK_sb4oc9lkns36wswku831c33w6  unique (code_value, code_designator, code_version);
alter table study drop scattered_storage;
alter table study add storage_ids varchar(255);
update study set storage_ids = (
  select list(distinct storage_id, '\')
  from location
    join instance on location.instance_fk = instance.pk
    join series on instance.series_fk = series.pk
  where study_fk = study.pk);

--not working-start--
alter table study alter storage_ids set not null;
create index UK_fypbtohf5skbd3bkyd792a6dt on study (storage_ids);
--not working-end--

alter table series add rejection_state integer;
update series set rejection_state = 1;
update series set rejection_state = 0 where not exists (
  select 1 from instance where series.pk = instance.series_fk and instance.reject_code_fk is not null);
update series set rejection_state = 2 where rejection_state = 1 and not exists (
  select 1 from instance where series.pk = instance.series_fk and instance.reject_code_fk is null);

--not working-start--
alter table series alter rejection_state set not null;
create index UK_jlgy9ifvqak4g2bxkchismw8x on series (rejection_state);
--not working-end--

alter table study add rejection_state integer;
update study set rejection_state = 1;
update study set rejection_state = 0 where not exists (
  select 1 from series where study.pk = series.study_fk and series.rejection_state != 0);
update study set rejection_state = 2 where not exists (
  select 1 from series where study.pk = series.study_fk and series.rejection_state != 2);

--not working-start--
alter table study alter rejection_state set not null;
create index UK_hwu9omd369ju3nufufxd3vof2 on study (rejection_state);
--not working-end--
