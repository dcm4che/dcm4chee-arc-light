-- part 1: can be applied on archive running archive 5.33
alter table ups add perf_name_fk bigint;
alter table ups add constraint UKhmvl80qis2pab8xtd9qyqea5b unique (perf_name_fk);
alter table ups add constraint FKhy3cd5se2avt08upapu19y1g6 foreign key (perf_name_fk) references person_name;

alter table patient_id add pat_name varchar(64);
UPDATE patient_id
SET pat_name = (
    SELECT
        TRIM(TRAILING '=' FROM (
            TRIM(TRAILING '^' FROM COALESCE(person_name.alphabetic_name, '')) || '=' ||
            TRIM(TRAILING '^' FROM COALESCE(person_name.ideographic_name, '')) || '=' ||
            TRIM(TRAILING '^' FROM COALESCE(person_name.phonetic_name, ''))
        ))
    FROM person_name
             JOIN patient ON person_name.pk = patient.pat_name_fk
    WHERE patient.pk = patient_id.patient_fk
);
update patient_id set pat_name = '*'
    where pat_name = '';

alter table series add metadata_update_load_objects smallint;
update series set metadata_update_load_objects = true
    where metadata_update_time is not null;
update series set metadata_update_load_objects = false
    where metadata_update_time is null;
create index IDX12auabn3ubq8bat0wkg33n3ms on series (created_time);

-- part 2: shall be applied on stopped archive before starting 5.34
update patient_id set pat_name = (
    SELECT
        TRIM(TRAILING '=' FROM (
            TRIM(TRAILING '^' FROM COALESCE(person_name.alphabetic_name, '')) || '=' ||
            TRIM(TRAILING '^' FROM COALESCE(person_name.ideographic_name, '')) || '=' ||
            TRIM(TRAILING '^' FROM COALESCE(person_name.phonetic_name, ''))
        ))
    from person_name
             join patient on person_name.pk = patient.pat_name_fk
    where patient.pk = patient_id.patient_fk)
where pat_name is null;

update patient_id set pat_name = '*'
    where pat_name = '';
update patient_id set pat_name = '*'
    where pat_name is null;
update patient_id set entity_id = '*'
    where entity_id is null;
update patient_id set entity_uid = '*', entity_uid_type = '*'
    where entity_uid is null;
alter table patient_id add constraint UKq9cv3b9n0uv93ugud52uiw9k1
    unique (pat_id, entity_id, entity_uid, entity_uid_type, pat_name);

update series set metadata_update_load_objects = true
    where metadata_update_time is not null and metadata_update_load_objects is null;
update series set metadata_update_load_objects = false
    where metadata_update_time is null and metadata_update_load_objects is null;

-- part 3: can be applied on already running archive 5.34
alter table patient_id
    alter entity_id set not null,
    alter entity_uid set not null,
    alter entity_uid_type set not null,
    alter pat_name set not null;

alter table series
    alter metadata_update_load_objects set not null;
