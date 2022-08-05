alter table queue_msg modify msg_props varchar2(4000 char);
alter table location modify object_size not null modify storage_path not null modify tsuid not null;
create table ian_task (pk number(19,0) not null, calling_aet varchar2(255 char) not null, device_name varchar2(255 char) not null, ian_dests varchar2(255 char) not null, scheduled_time timestamp, study_iuid varchar2(255 char), mpps_fk number(19,0), primary key (pk));
alter table ian_task add constraint UK_dq88edcjjxh7h92f89y5ueast  unique (study_iuid);
create index UK_5shiir23exao1xpy2n5gvasrh on ian_task (device_name);
alter table ian_task add constraint FK_1fuh251le2hid2byw90hd1mly foreign key (mpps_fk) references mpps;
create sequence ian_task_pk_seq;
update code set code_version = '*' where code_version is null;
alter table code modify code_version not null;
alter table code drop constraint UK_l01jou0o1rohy7a9p933ndrxg;
alter table code add constraint UK_sb4oc9lkns36wswku831c33w6  unique (code_value, code_designator, code_version);
alter table study drop column scattered_storage;
alter table study add (storage_ids varchar2(255 char));
update study set storage_ids = (
  select storage_id
    from location
      join instance on location.instance_fk = instance.pk
      join series on instance.series_fk = series.pk
    where study_fk = study.pk and rownum = 1);
alter table study modify storage_ids not null;
create index UK_fypbtohf5skbd3bkyd792a6dt on study (storage_ids);
alter table series add (rejection_state number(10,0));
update series set rejection_state = 1;
update series set rejection_state = 0 where not exists (
  select 1 from instance where series.pk = instance.series_fk and instance.reject_code_fk is not null);
update series set rejection_state = 2 where rejection_state = 1 and not exists (
  select 1 from instance where series.pk = instance.series_fk and instance.reject_code_fk is null);
alter table series modify rejection_state not null;
create index UK_jlgy9ifvqak4g2bxkchismw8x on series (rejection_state);
alter table study add (rejection_state number(10,0));
update study set rejection_state = 1;
update study set rejection_state = 0 where not exists (
  select 1 from series where study.pk = series.study_fk and series.rejection_state != 0);
update study set rejection_state = 2 where not exists (
  select 1 from series where study.pk = series.study_fk and series.rejection_state != 2);
alter table study modify rejection_state not null;
create index UK_hwu9omd369ju3nufufxd3vof2 on study (rejection_state);
