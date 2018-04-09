-- can be applied on running archive 5.11
alter table queue_msg add device_name varchar2(255 char);
alter table series add sop_cuid varchar2(255 char);
alter table series add tsuid varchar2(255 char);

create index UK_kvtxqtdow67hcr2wv8irtdwqy on queue_msg (device_name);
create index UK_jfyulc3fo7cmn29sbha0l72m3 on queue_msg (created_time);
create index UK_7iil4v32vf234i75edsxkdr8f on export_task (created_time);
create index UK_sf2g7oi8cfx89olwch9095hx7 on retrieve_task (created_time);
create index UK_e2lo4ep4t4k07njc09anf6xkm on retrieve_task (updated_time);
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
