-- part 1: can be applied on archive running archive 5.19
alter table export_task add batch_id varchar2(255 char);
create index UK_mt8p2iqcmkoxodkjtfcw1635v on export_task (batch_id);

-- part 2: shall be applied on stopped archive before starting 5.20

-- part 3: can be applied on already running archive 5.20
