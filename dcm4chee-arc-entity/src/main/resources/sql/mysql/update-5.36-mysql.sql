-- part 1: can be applied on archive running archive 5.35
alter table ups
    add institution varchar(255),
    add department varchar(255),
    add inst_code_fk bigint,
    add dept_code_fk bigint;

update ups set institution='*', department='*';

create index IDXi74frwq9xkdswyg0cbw0vd3hy on ups (institution);
create index IDXrirxs9tt1bpxqe9o5v27jb64i on ups (department);

-- also creates the indexes FKjox6k6jwo072bp7isqa8nr99v and FKrkiwdhphbhbfk2lmyp55axis on the FK columns
alter table ups add constraint FKjox6k6jwo072bp7isqa8nr99v foreign key (inst_code_fk) references code (pk);
alter table ups add constraint FKrkiwdhphbhbfk2lmyp55axis foreign key (dept_code_fk) references code (pk);

alter table patient_id
    add created_time datetime(6);

update patient_id set created_time=(
    select patient.created_time
        from patient
        where patient.pk = patient_id.patient_fk);

alter table metadata
    add num_instances integer;

update metadata set num_instances=(
    select count(instance.pk)
        from instance join series on series.pk = instance.series_fk
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
        from instance join series on series.pk = instance.series_fk
        where series.metadata_fk = metadata.pk)
    where num_instances is null;

-- part 3: can be applied on already running archive 5.36
alter table ups
    modify institution varchar(255) not null,
    modify department varchar(255) not null;

alter table patient_id modify created_time datetime(6) not null;

alter table metadata modify num_instances integer not null;
