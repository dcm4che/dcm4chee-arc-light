-- part 1: can be applied on archive running archive 5.33
alter table ups add perf_name_fk number(19,0) unique;
alter table ups add constraint FKhy3cd5se2avt08upapu19y1g6 foreign key (perf_name_fk) references person_name;

alter table patient_id add pat_name varchar2(255 char);
UPDATE patient_id
SET pat_name = (
    SELECT RTRIM(
                   RTRIM(
                           RTRIM(NVL(person_name.alphabetic_name, ''), '^') || '=' ||
                           RTRIM(NVL(person_name.ideographic_name, ''), '^') || '=' ||
                           RTRIM(NVL(person_name.phonetic_name, ''), '^'),
                           '='),
                   '=')
    FROM person_name
             JOIN patient ON person_name.pk = patient.pat_name_fk
    WHERE patient.pk = patient_id.patient_fk
);
update patient_id set pat_name = '*'
    where pat_name is null;

alter table series add metadata_update_load_objects number(1,0);
update series set metadata_update_load_objects = 1
    where metadata_update_time is not null;
update series set metadata_update_load_objects = 0
    where metadata_update_time is null;
create index IDX12auabn3ubq8bat0wkg33n3ms on series (created_time);

-- part 2: shall be applied on stopped archive before starting 5.34
update patient_id set pat_name = (
    SELECT RTRIM(
                   RTRIM(
                           RTRIM(NVL(person_name.alphabetic_name, ''), '^') || '=' ||
                           RTRIM(NVL(person_name.ideographic_name, ''), '^') || '=' ||
                           RTRIM(NVL(person_name.phonetic_name, ''), '^'),
                           '='),
                   '=')
    from person_name
             join patient on person_name.pk = patient.pat_name_fk
    where patient.pk = patient_id.patient_fk)
where pat_name is null;

update patient_id set pat_name = '*'
    where pat_name is null;
update patient_id set entity_id = '*'
    where entity_id is null;
update patient_id set entity_uid = '*', entity_uid_type = '*'
    where entity_uid is null;
alter table patient_id add constraint SYS_C007925
    unique (pat_id, entity_id, entity_uid, entity_uid_type, pat_name);

update series set metadata_update_load_objects = 1
    where metadata_update_time is not null and metadata_update_load_objects is null;
update series set metadata_update_load_objects = 0
    where metadata_update_time is null and metadata_update_load_objects is null;

-- part 3: can be applied on already running archive 5.34
alter table patient_id
    modify entity_id varchar2(255 char) not null;
alter table patient_id
    modify entity_uid varchar2(255 char) not null;
alter table patient_id
    modify entity_uid_type varchar2(255 char) not null;
alter table patient_id
    modify pat_name varchar2(255 char) not null;

alter table series
    modify metadata_update_load_objects number(1,0) not null;
