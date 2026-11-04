-- part 1: can be applied on archive running archive 5.35
alter table ups
    add institution varchar(255),
    add department varchar(255),
    add inst_code_fk bigint,
    add dept_code_fk bigint;

update ups set institution='*', department='*';

create index IDXi74frwq9xkdswyg0cbw0vd3hy on ups (institution);
create index IDXrirxs9tt1bpxqe9o5v27jb64i on ups (department);
create index FKjox6k6jwo072bp7isqa8nr99v on ups (inst_code_fk);
create index FKrkiwdhphbhbfk2lmyp55axis on ups (dept_code_fk);

alter table patient_id
    add created_time timestamp(6);

update patient_id set created_time=(
    select patient.created_time
        from patient
        where patient.pk = patient_id.patient_fk);

alter table metadata
    add num_instances integer;

update metadata set num_instances=(
    select count(instance.pk)
        from instance join series on series_fk=instance.series_fk
        where series.metadata_fk = metadata.pk);

-- part 2: shall be applied on stopped archive before starting 5.36
update ups set institution='*' where institution is null;
update ups set department='*' where department is null;

update patient_id set created_time=(
    select patient.created_time
        from patient
        where patient.pk = patient_id.patient_fk)
    where created_time is null;

update metadata set num_instances=(
    select count(instance.pk)
        from instance join series on series_fk=instance.series_fk
        where series.metadata_fk = metadata.pk)
    where num_instances is null;

-- part 3: can be applied on already running archive 5.36
alter table ups alter institution set not null;
alter table ups alter department set not null;

alter table patient_id alter created_time set not null;

alter table metadata alter num_instances set not null;
