alter table series add failed_retrieves integer;
alter table series add failed_iuids varchar(4000);
update series set failed_retrieves = 0;
alter table series alter column failed_retrieves set not null;
