-- can be applied on running archive 5.11
alter table queue_msg add device_name varchar(255);
alter table series add sop_cuid varchar(255);
alter table series add tsuid varchar(255);

create index UK_kvtxqtdow67hcr2wv8irtdwqy on queue_msg (device_name);
create index UK_mrn00m45lkq1xbehmbw5d9jbl on series (sop_cuid);
create index UK_tahx0q1ejidnsam40ans7oecx on series (tsuid);

-- shall be applied on stopped archive before starting 5.11
update queue_msg set device_name = (select device_name
  from retrieve_task
  where queue_msg_fk = queue_msg.pk);
update queue_msg set device_name = (select device_name
  from export_task
  where queue_msg_fk = queue_msg.pk);
update queue_msg set device_name = 'dcm4chee-arc'
  where device_name is null;

-- may be already applied on running archive 5.11 to minimize downtime
-- and re-applied on stopped archive only on series inserted after the previous update (where series.pk > xxx)
update series set (sop_cuid, tsuid) = (
  select sop_cuid, tsuid
  from instance join location on instance_fk = instance.pk
  where series_fk = series.pk and object_type = 0 and rownum=1);

alter table queue_msg modify device_name not null;

alter table series modify sop_cuid not null;
alter table series modify tsuid not null;
alter table retrieve_task drop column device_name;
