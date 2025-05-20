-- part 1: can be applied on archive running archive 5.32
alter table ups add perf_name_fk bigint unique;
alter table ups add constraint FKhy3cd5se2avt08upapu19y1g6 foreign key (perf_name_fk) references person_name(pk);
create index FKhy3cd5se2avt08upapu19y1g6 on ups (perf_name_fk) ;

alter table patient_id add pat_name varchar(255);
update patient_id set pat_name = (
    select RTRIM(
                   REGEXP_REPLACE(
                           CONCAT(
                                   REGEXP_REPLACE(person_name.alphabetic_name, '\\^+$', ''),
                                   '=',
                                   REGEXP_REPLACE(person_name.ideographic_name, '\\^+$', ''),
                                   '=',
                                   REGEXP_REPLACE(person_name.phonetic_name, '\\^+$', '')
                           ),
                           '=+$',
                           ''
                   )
           )
from person_name
         join patient on person_name.pk = patient.pat_name_fk
where patient.pk = patient_id.patient_fk );

update patient_id set pat_name = '*'
    where pat_name is null;

alter table series add metadata_update_load_objects bit;
update series set metadata_update_load_objects = true
    where metadata_update_time is not null;
update series set metadata_update_load_objects = false
    where metadata_update_time is null;

-- part 2: shall be applied on stopped archive before starting 5.33
update patient_id set pat_name = (
    select RTRIM(
                   REGEXP_REPLACE(
                           CONCAT(
                                   REGEXP_REPLACE(person_name.alphabetic_name, '\\^+$', ''),
                                   '=',
                                   REGEXP_REPLACE(person_name.ideographic_name, '\\^+$', ''),
                                   '=',
                                   REGEXP_REPLACE(person_name.phonetic_name, '\\^+$', '')
                           ),
                           '=+$',
                           ''
                   )
           )
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

--constraint part needs to be done - following doesn't work
alter table patient_id add constraint patient_id_pat_id_entity_id_entity_uid_entity_uid_type_pat__key
    unique (pat_id, entity_id, entity_uid, entity_uid_type, pat_name);
--from generated create-mysql.sql for 5.34 - following doesn't work
-- alter table patient_id add constraint UKq9cv3b9n0uv93ugud52uiw9k1 unique (pat_id, entity_id, entity_uid, entity_uid_type, pat_name);
--also other indexes f.e. create index IDXm2jq6xe87vegohf6g10t5ptew on patient_id (entity_uid(64), entity_uid_type(64));
-- don't reflect in any of the create-mysql.sql files

update series set metadata_update_load_objects = true
    where metadata_update_time is not null and metadata_update_load_objects is null;
update series set metadata_update_load_objects = false
    where metadata_update_time is null and metadata_update_load_objects is null;

-- part 3: can be applied on already running archive 5.33
alter table patient_id
    modify entity_id varchar(255) not null,
    modify entity_uid varchar(255) not null,
    modify entity_uid_type varchar(255) not null,
    modify pat_name varchar(255) not null;

alter table series
    modify metadata_update_load_objects bit not null;