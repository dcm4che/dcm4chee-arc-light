-- part 1: can be applied on archive running archive 5.33
alter table ups add perf_name_fk bigint;
create unique nonclustered index UKhmvl80qis2pab8xtd9qyqea5b on ups (perf_name_fk) where perf_name_fk is not null;
alter table ups add constraint FKhy3cd5se2avt08upapu19y1g6 foreign key (perf_name_fk) references person_name;
create index FKhy3cd5se2avt08upapu19y1g6 on ups (perf_name_fk) ;

alter table patient_id add pat_name nvarchar(170);
CREATE FUNCTION dbo.RTrimChar (
    @input NVARCHAR(MAX),
    @trim_char NCHAR(1)
)
    RETURNS NVARCHAR(MAX)
                    AS
BEGIN
    DECLARE @i INT = LEN(@input);

    WHILE @i > 0 AND SUBSTRING(@input, @i, 1) = @trim_char
BEGIN
            SET @i = @i - 1;
END
    RETURN LEFT(@input, @i);
END
--for testing : SELECT dbo.RTrimChar(N'ﾔﾏﾀﾞ^ﾀﾛｳ^^^^', '^') OR SELECT dbo.RTrimChar(N'Buc^Jérôme==', '=')
UPDATE patient_id
SET pat_name = (
    SELECT
        RTRIM(dbo.RTrimChar(
                RTRIM(CONCAT(
                        dbo.RTrimChar(person_name.alphabetic_name, '^'),
                        '=',
                        dbo.RTrimChar(person_name.ideographic_name, '^'),
                        '=',
                        dbo.RTrimChar(person_name.phonetic_name, '^'))),
                '='))
    FROM person_name
             JOIN patient ON person_name.pk = patient.pat_name_fk
    WHERE patient.pk = patient_id.patient_fk
);
update patient_id set pat_name = '*'
where pat_name is null;

alter table series add metadata_update_load_objects bit;
update series set metadata_update_load_objects = 1
where metadata_update_time is not null;
update series set metadata_update_load_objects = 0
where metadata_update_time is null;
create index IDX12auabn3ubq8bat0wkg33n3ms on series (created_time);

-- part 2: shall be applied on stopped archive before starting 5.34
UPDATE patient_id
SET pat_name = (
    SELECT
        RTRIM(dbo.RTrimChar(
                RTRIM(CONCAT(
                        dbo.RTrimChar(person_name.alphabetic_name, '^'),
                        '=',
                        dbo.RTrimChar(person_name.ideographic_name, '^'),
                        '=',
                        dbo.RTrimChar(person_name.phonetic_name, '^'))),
                '='))
    FROM person_name
             JOIN patient ON person_name.pk = patient.pat_name_fk
    WHERE patient.pk = patient_id.patient_fk)
where pat_name is null;
update patient_id set pat_name = '*'
    where pat_name is null;

update patient_id set entity_id = '*'
    where entity_id is null;
update patient_id set entity_uid = '*', entity_uid_type = '*'
    where entity_uid is null;
alter table patient_id add constraint UKq9cv3b9n0uv93ugud52uiw9k1
    unique (pat_id, entity_id, entity_uid, entity_uid_type, pat_name);

update series set metadata_update_load_objects = 1
    where metadata_update_time is not null and metadata_update_load_objects is null;
update series set metadata_update_load_objects = 0
    where metadata_update_time is null and metadata_update_load_objects is null;

-- part 3: can be applied on already running archive 5.34
ALTER TABLE patient_id DROP CONSTRAINT UKq9cv3b9n0uv93ugud52uiw9k1;
drop index IDXd1sdyupb0vwvx23jownjnyy72 on patient_id;
drop index IDXm2jq6xe87vegohf6g10t5ptew on patient_id;
alter table patient_id alter column entity_id varchar(255) not null;
alter table patient_id alter column entity_uid varchar(255) not null;
alter table patient_id alter column entity_uid_type varchar(255) not null;
create index IDXd1sdyupb0vwvx23jownjnyy72 on patient_id (entity_id);
create index IDXm2jq6xe87vegohf6g10t5ptew on patient_id (entity_uid, entity_uid_type);
alter table patient_id alter column pat_name nvarchar(170) not null;
alter table patient_id add constraint UKq9cv3b9n0uv93ugud52uiw9k1
    unique (pat_id, entity_id, entity_uid, entity_uid_type, pat_name);

alter table series alter column metadata_update_load_objects bit not null;
