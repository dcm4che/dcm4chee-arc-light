-- part 1: can be applied on archive running archive 5.33
alter table ups add perf_name_fk bigint unique;
alter table if exists ups add constraint FKhy3cd5se2avt08upapu19y1g6 foreign key (perf_name_fk) references person_name;
create index FKhy3cd5se2avt08upapu19y1g6 on ups (perf_name_fk) ;

alter table patient_id add pat_name varchar(255);
update patient_id set pat_name = (
    select rtrim(concat(
        rtrim(person_name.alphabetic_name, '^'), '=',
        rtrim(person_name.ideographic_name, '^'), '=',
        rtrim(person_name.phonetic_name, '^')), '=')
    from person_name
        join patient on person_name.pk = patient.pat_name_fk
        where patient.pk = patient_id.patient_fk );
update patient_id set pat_name = '*'
    where pat_name is null;

alter table series add metadata_update_load_objects boolean;
update series set metadata_update_load_objects = true
    where metadata_update_time is not null;
update series set metadata_update_load_objects = false
    where metadata_update_time is null;

-- part 2: shall be applied on stopped archive before starting 5.34
update patient_id set pat_name = (
    select rtrim(concat(
        rtrim(person_name.alphabetic_name, '^'), '=',
        rtrim(person_name.ideographic_name, '^'), '=',
        rtrim(person_name.phonetic_name, '^')), '=')
    from person_name
        join patient on person_name.pk = patient.pat_name_fk
        where patient.pk = patient_id.patient_fk)
    where pat_name is null;
update patient_id set pat_name = '*'
    where pat_name is null;
alter table patient_id add constraint patient_id_pat_id_pat_name_key
    unique (pat_id, pat_name);

update series set metadata_update_load_objects = true
    where metadata_update_time is not null and metadata_update_load_objects is null;
update series set metadata_update_load_objects = false
    where metadata_update_time is null and metadata_update_load_objects is null;

-- part 3: can be applied on already running archive 5.34
alter table patient_id
    alter pat_name set not null;

alter table series
    alter metadata_update_load_objects set not null;
