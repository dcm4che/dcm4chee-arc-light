-- part 1: can be applied on archive running archive 5.34
create table study_access_control_id (study_fk bigint not null, access_control_id varchar(255));
alter table if exists study_access_control_id add constraint FKluah7q5kdwqidu6uwlrsndd7k foreign key (study_fk) references study;

create table series_access_control_id (series_fk bigint not null, access_control_id varchar(255));
alter table if exists series_access_control_id add constraint FKt7uu3btv6pro4wuxspq0pom8y foreign key (series_fk) references series;

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
drop index IDXr9qbr5jv4ejclglvyvtsynuo9
