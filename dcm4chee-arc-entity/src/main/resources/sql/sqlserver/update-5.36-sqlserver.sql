-- part 1: can be applied on archive running archive 5.35
alter table ups add
    institution varchar(255) not null constraint DF_ups_institution default '*',
    department varchar(255) not null constraint DF_ups_department default '*',
    inst_code_fk bigint,
    dept_code_fk bigint;

create index IDXi74frwq9xkdswyg0cbw0vd3hy on ups (institution);
create index IDXrirxs9tt1bpxqe9o5v27jb64i on ups (department);

alter table ups add constraint FKjox6k6jwo072bp7isqa8nr99v foreign key (inst_code_fk) references code;
alter table ups add constraint FKrkiwdhphbhbfk2lmyp55axis foreign key (dept_code_fk) references code;

create index FKjox6k6jwo072bp7isqa8nr99v on ups (inst_code_fk) ;
create index FKrkiwdhphbhbfk2lmyp55axis on ups (dept_code_fk) ;

alter table patient_id add created_time datetime2(6);
alter table patient_id add updated_time datetime2(6);

update patient_id set created_time=(
    select patient.created_time
        from patient
        where patient.pk = patient_id.patient_fk);
update patient_id set updated_time = created_time;

alter table metadata add num_instances int not null constraint DF_metadata_num_instances default 0;

update metadata set num_instances = (
    select count(instance.pk)
        from instance join series on series.pk = instance.series_fk
        where series.metadata_fk = metadata.pk)
    where pk in (select series.metadata_fk from series where inst_purge_state <> 1);

-- part 2: shall be applied on stopped archive before starting 5.36
update patient_id set created_time=(
    select patient.created_time
        from patient
        where patient.pk = patient_id.patient_fk)
    where created_time is null;
update patient_id set updated_time = created_time where updated_time is null;

update metadata set num_instances = (
    select count(instance.pk)
        from instance join series on series.pk = instance.series_fk
        where series.metadata_fk = metadata.pk)
    where num_instances = 0
        and pk in (select series.metadata_fk from series where inst_purge_state <> 1);

-- part 3: can be applied on already running archive 5.36
alter table ups drop constraint DF_ups_institution;
alter table ups drop constraint DF_ups_department;

alter table patient_id alter column created_time datetime2(6) not null;
alter table patient_id alter column updated_time datetime2(6) not null;
alter table patient_id drop column pat_id_type_code;

alter table metadata drop constraint DF_metadata_num_instances;
