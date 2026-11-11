-- part 1: can be applied on archive running archive 5.35
alter table ups add (
    institution varchar2(255 char),
    department varchar2(255 char),
    inst_code_fk number(19,0),
    dept_code_fk number(19,0));

update ups set institution='*', department='*';

create index IDXi74frwq9xkdswyg0cbw0vd3hy on ups (institution);
create index IDXrirxs9tt1bpxqe9o5v27jb64i on ups (department);

alter table ups add constraint FKjox6k6jwo072bp7isqa8nr99v foreign key (inst_code_fk) references code;
alter table ups add constraint FKrkiwdhphbhbfk2lmyp55axis foreign key (dept_code_fk) references code;

create index FKjox6k6jwo072bp7isqa8nr99v on ups (inst_code_fk) ;
create index FKrkiwdhphbhbfk2lmyp55axis on ups (dept_code_fk) ;

alter table patient_id add created_time timestamp(6);

update patient_id set created_time=(
    select patient.created_time
        from patient
        where patient.pk = patient_id.patient_fk);

alter table metadata add num_instances number(10,0);

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
alter table ups modify institution not null;
alter table ups modify department not null;

alter table patient_id modify created_time not null;

alter table metadata modify num_instances not null;
