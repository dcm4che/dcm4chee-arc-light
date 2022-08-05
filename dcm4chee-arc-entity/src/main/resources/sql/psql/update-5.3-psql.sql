alter table series add failed_retrieves int4, add failed_iuids varchar(4000);
update series set failed_retrieves = 0;
alter table series alter failed_retrieves set not null;
