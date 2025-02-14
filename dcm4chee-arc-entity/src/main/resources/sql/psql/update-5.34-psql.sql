-- part 1: can be applied on archive running archive 5.33
alter table ups add perf_name_fk bigint unique;
alter table if exists ups add constraint FKhy3cd5se2avt08upapu19y1g6 foreign key (perf_name_fk) references person_name;
create index FKhy3cd5se2avt08upapu19y1g6 on ups (perf_name_fk) ;
update patient_id set entity_id = entity_uid
    where entity_id is null and entity_uid is not null;
update patient_id set entity_uid = concat('iss:', entity_id), entity_uid_type = 'URI'
    where entity_uid icds null and entity_id is not null;
update patient_id set entity_id = 'unknown', entity_uid = 'iss:unknown', entity_uid_type = 'URI'
    where entity_id is null;

-- part 2: shall be applied on stopped archive before starting 5.34
update patient_id set entity_id = entity_uid
    where entity_id is null and entity_uid is not null;
update patient_id set entity_uid = concat('iss:', entity_id), entity_uid_type = 'URI'
    where entity_uid is null and entity_id is not null;
update patient_id set entity_id = 'unknown', entity_uid = 'iss:unknown', entity_uid_type = 'URI'
    where entity_id is null;
alter table patient_id add constraint patient_id_pat_id_entity_id_entity_uid_entity_uid_type_key
    unique (pat_id, entity_id, entity_uid, entity_uid_type);
-- if adding the constraint fails with "could not create unique index", invoke
--
-- update patient_id set entity_id  = 'unknown.1', entity_uid = 'iss:unknown.1' where pk = (
--    select pk from patient_id where entity_id = 'unknown' and pat_id in (
--       select pat_id from patient_id where entity_id = 'unknown' group by pat_id having count(*) > 1)
--    limit 1);
--
-- and retry. If it still fails, invoke
--
-- update patient_id set entity_id  = 'unknown.2', entity_uid = 'iss:unknown.2' where pk = (
--    select pk from patient_id where entity_id = 'unknown' and pat_id in (
--       select pat_id from patient_id where entity_id = 'unknown' group by pat_id having count(*) > 1)
--    limit 1);
--
-- and so on, until adding the constraint succeeds.

-- part 3: can be applied on already running archive 5.34
alter table patient_id
    alter entity_id set not null,
    alter entity_uid set not null,
    alter entity_uid_type set not null;
