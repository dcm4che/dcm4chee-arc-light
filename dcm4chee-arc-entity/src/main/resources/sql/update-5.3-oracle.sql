alter table series add failed_retrieves number(10,0), add failed_iuids varchar2(4000 char);
update series set failed_retrieves = 0;
alter table series modify failed_retrieves not null;
