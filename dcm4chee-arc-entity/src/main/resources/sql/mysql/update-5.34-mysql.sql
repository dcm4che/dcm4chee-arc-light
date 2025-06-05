-- part 1: can be applied on archive running archive 5.33
alter table ups add perf_name_fk bigint unique;
alter table ups add constraint UKhmvl80qis2pab8xtd9qyqea5b unique (perf_name_fk);
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
create index IDX12auabn3ubq8bat0wkg33n3ms on series (created_time);

-- part 2: shall be applied on stopped archive before starting 5.34
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

alter table patient_id add constraint UKq9cv3b9n0uv93ugud52uiw9k1
    unique (pat_id(128), entity_id(128), entity_uid(128), entity_uid_type(128), pat_name(128));

update series set metadata_update_load_objects = true
    where metadata_update_time is not null and metadata_update_load_objects is null;
update series set metadata_update_load_objects = false
    where metadata_update_time is null and metadata_update_load_objects is null;

-- part 3: can be applied on already running archive 5.34
alter table patient_id
    modify entity_id varchar(255) not null,
    modify entity_uid varchar(255) not null,
    modify entity_uid_type varchar(255) not null,
    modify pat_name varchar(255) not null;

alter table series
    modify metadata_update_load_objects bit not null;