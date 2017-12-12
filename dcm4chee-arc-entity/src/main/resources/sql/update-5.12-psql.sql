-- can be applied on running archive 5.11
alter table queue_msg add device_name varchar(255);
create index UK_kvtxqtdow67hcr2wv8irtdwqy on queue_msg (device_name);

-- shall be applied on stopped archive before starting 5.11
update queue_msg set device_name = retrieve_task.device_name
  from retrieve_task
  where queue_msg_fk = queue_msg.pk;
update queue_msg set device_name = export_task.device_name
  from export_task
  where queue_msg_fk = queue_msg.pk;
update queue_msg set device_name = 'dcm4chee-arc'
  where device_name is null;

alter table queue_msg alter device_name set not null;
alter table retrieve_task drop device_name;
