-- part 1: can be applied on archive running archive 5.15
alter table study
  add expiration_state int4,
  add expiration_exporter_id varchar(255);

alter table series
  add expiration_state int4,
  add expiration_exporter_id varchar(255);

update study set expiration_state = 0;
update series set expiration_state = 0;
create index UK_fyasyw3wco6hoj2entr7l6d09 on study (expiration_state);
create index UK_ih49lthl3udoca5opvgsdcerj on series (expiration_state);
create index UK_9fi64g5jjycg9dp24jjk5txg1 on series (series_iuid);

-- part 2: shall be applied on stopped archive before starting 5.16
update study set expiration_state = 0 where expiration_state is null;
update series set expiration_state = 0 where expiration_state is null;

-- part 3: can be applied on already running archive 5.16
alter table study alter expiration_state set not null;
alter table series alter expiration_state set not null;
