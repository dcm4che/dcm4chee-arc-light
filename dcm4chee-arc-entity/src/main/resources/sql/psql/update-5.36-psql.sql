-- part 1: can be applied on archive running archive 5.35
alter table ups
    add institution varchar(255) not null default '*',
    add department varchar(255) not null default '*',
    add inst_code_fk bigint,
    add dept_code_fk bigint;

create index IDXi74frwq9xkdswyg0cbw0vd3hy on ups (institution);
create index IDXrirxs9tt1bpxqe9o5v27jb64i on ups (department);
create index FKjox6k6jwo072bp7isqa8nr99v on ups (inst_code_fk);
create index FKrkiwdhphbhbfk2lmyp55axis on ups (dept_code_fk);

alter table if exists ups add constraint FKjox6k6jwo072bp7isqa8nr99v foreign key (inst_code_fk) references code;
alter table if exists ups add constraint FKrkiwdhphbhbfk2lmyp55axis foreign key (dept_code_fk) references code;

alter table patient_id
    add created_time timestamp(6),
    add updated_time timestamp(6);

update patient_id set created_time=(
    select patient.created_time
        from patient
        where patient.pk = patient_id.patient_fk);

update patient_id set updated_time = created_time;

alter table metadata
    add num_instances integer not null default 0;

update metadata set num_instances = (
    select count(instance.pk)
        from instance join series on series_fk=series.pk
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
        from instance join series on series_fk=series.pk
        where series.metadata_fk = metadata.pk)
    where num_instances = 0
        and pk in (select series.metadata_fk from series where inst_purge_state <> 1);

-- part 3: can be applied on already running archive 5.36
alter table ups alter institution drop default;
alter table ups alter department drop default;

alter table patient_id alter created_time set not null;
alter table patient_id alter updated_time set not null;
alter table patient_id drop pat_id_type_code;

alter table metadata alter num_instances drop default;
