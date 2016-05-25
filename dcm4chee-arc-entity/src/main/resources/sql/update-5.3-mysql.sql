alter table series add failed_retrieves integer, add failed_iuids varchar(4000);
update series set failed_retrieves = 0;
alter table series modify failed_retrieves integer not null;
