-- part 1: can be applied on archive running archive 5.34
create table study_access_control_id (study_fk bigint not null, access_control_id varchar(255));
SELECT COUNT(*)
FROM SYSCAT.TABCONST
WHERE TABNAME = 'STUDY_ACCESS_CONTROL_ID'
  AND CONSTNAME = 'FKLUAH7Q5KDWQIDU6UWLRSNDD7K';
--if returns 0, execute next
ALTER TABLE study_access_control_id
    ADD CONSTRAINT FKluah7q5kdwqidu6uwlrsndd7k
        FOREIGN KEY (study_fk)
            REFERENCES study(pk);

create table series_access_control_id (series_fk bigint not null, access_control_id varchar(255));
SELECT COUNT(*)
FROM SYSCAT.TABCONST
WHERE TABNAME = 'SERIES_ACCESS_CONTROL_ID'
  AND CONSTNAME = 'FKt7uu3btv6pro4wuxspq0pom8y';
--if returns 0, execute next
ALTER TABLE series_access_control_id
    ADD CONSTRAINT FKt7uu3btv6pro4wuxspq0pom8y
        FOREIGN KEY (series_fk)
            REFERENCES series(pk);

alter table series add num_instances integer;

-- part 2: shall be applied on stopped archive before starting 5.35
insert into study_access_control_id(study_fk, access_control_id)
select study.pk, study.access_control_id
from study where access_control_id != '*';
create index IDXl3g07pncx4agpayus50wn8u40 on study_access_control_id (access_control_id);
create index FKluah7q5kdwqidu6uwlrsndd7k on study_access_control_id (study_fk) ;

insert into series_access_control_id(series_fk, access_control_id)
select series.pk, series.access_control_id
from series where access_control_id != '*';
create index IDXmt8f5xaiqgiwps1j4riuge3lm on series_access_control_id (access_control_id);
create index FKt7uu3btv6pro4wuxspq0pom8y on series_access_control_id (series_fk) ;

alter table study alter access_control_id drop not null;
alter table series alter access_control_id drop not null;

-- part 3: can be applied on already running archive 5.35
drop index IDX24av2ewa70e7cykl340n63aqd;
drop index IDXr9qbr5jv4ejclglvyvtsynuo9;