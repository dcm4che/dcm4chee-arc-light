-- part 1: can be applied on archive running archive 5.33
alter table ups add perf_name_fk bigint;
create unique index UKhmvl80qis2pab8xtd9qyqea5b on ups (perf_name_fk) exclude null keys;
alter table ups add constraint FKhy3cd5se2avt08upapu19y1g6 foreign key (perf_name_fk) references person_name;
create index FKhy3cd5se2avt08upapu19y1g6 on ups (perf_name_fk) ;

alter table patient_id add pat_name varchar(64);
UPDATE patient_id
SET pat_name = (
    SELECT
        REGEXP_REPLACE(
                REGEXP_REPLACE(
                        REGEXP_REPLACE(
                                COALESCE(person_name.alphabetic_name, ''),
                                '\^+$', '', 1, 0, 'c') || '=' ||
                        REGEXP_REPLACE(
                                COALESCE(person_name.ideographic_name, ''),
                                '\^+$', '', 1, 0, 'c') || '=' ||
                        REGEXP_REPLACE(
                                COALESCE(person_name.phonetic_name, ''),
                                '\^+$', '', 1, 0, 'c'),
                        '=+$', '', 1, 0, 'c'),
                '\^+$', '', 1, 0, 'c') -- optional final cleanup
    FROM person_name
             JOIN patient ON person_name.pk = patient.pat_name_fk
    WHERE patient.pk = patient_id.patient_fk
);
update patient_id set pat_name = '*'
    where pat_name is null;

alter table series add metadata_update_load_objects smallint;
update series set metadata_update_load_objects = true
    where metadata_update_time is not null;
update series set metadata_update_load_objects = false
    where metadata_update_time is null;

-- part 2: shall be applied on stopped archive before starting 5.34
update patient_id set pat_name = (
    SELECT
        REGEXP_REPLACE(
                REGEXP_REPLACE(
                        REGEXP_REPLACE(
                                COALESCE(person_name.alphabetic_name, ''),
                                '\^+$', '', 1, 0, 'c') || '=' ||
                        REGEXP_REPLACE(
                                COALESCE(person_name.ideographic_name, ''),
                                '\^+$', '', 1, 0, 'c') || '=' ||
                        REGEXP_REPLACE(
                                COALESCE(person_name.phonetic_name, ''),
                                '\^+$', '', 1, 0, 'c'),
                        '=+$', '', 1, 0, 'c'),
                '\^+$', '', 1, 0, 'c') -- optional final cleanup
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

--required to set column as not null, else throws f.e.
--[42831][-542] The column named "ENTITY_ID" cannot be a column of a primary key or unique key constraint because it can contain null values.. SQLCODE=-542, SQLSTATE=42831, DRIVER=4.33.31
alter table patient_id
    alter entity_id set not null;
alter table patient_id
    alter entity_uid set not null;
alter table patient_id
    alter entity_uid_type set not null;
alter table patient_id
    alter pat_name set not null;

--required to reduce column lengths, else throws
--[54008][-613] The primary key, unique key, or table partitioning key identified by "UKQ9CV3B9N0UV93UGUD52UIW9K1" is too long or has too many columns and periods.. SQLCODE=-613, SQLSTATE=54008, DRIVER=4.33.31
ALTER TABLE patient_id ALTER COLUMN pat_id SET DATA TYPE VARCHAR(64);
ALTER TABLE patient_id ALTER COLUMN entity_id SET DATA TYPE VARCHAR(64);
ALTER TABLE patient_id ALTER COLUMN entity_uid SET DATA TYPE VARCHAR(64);
ALTER TABLE patient_id ALTER COLUMN entity_uid_type SET DATA TYPE VARCHAR(64);

alter table patient_id add constraint UKq9cv3b9n0uv93ugud52uiw9k1
    unique (pat_id, entity_id, entity_uid, entity_uid_type, pat_name);

update series set metadata_update_load_objects = true
    where metadata_update_time is not null and metadata_update_load_objects is null;
update series set metadata_update_load_objects = false
    where metadata_update_time is null and metadata_update_load_objects is null;

-- part 3: can be applied on already running archive 5.34
alter table series
    alter metadata_update_load_objects set not null;
