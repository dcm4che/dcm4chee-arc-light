-- part 1: can be applied on archive running archive 5.16
alter table queue_msg add batch_id varchar(255);
alter table retrieve_task add batch_id varchar(255);
alter table retrieve_task alter column queue_msg_fk drop not null;
alter table stgcmt_result alter column exporter_id drop not null;

update queue_msg set batch_id = batchID;
update retrieve_task set batch_id = queue_msg.batch_id
  from queue_msg
  where queue_msg_fk = queue_msg.pk;

create index UK_ln9rs61la03lhvgiv8c2wehnr on queue_msg (batch_id);
create index UK_ahkqwir2di2jm44jlhi22iw3e on retrieve_task (batch_id);

-- part 2: shall be applied on stopped archive before starting 5.17
update queue_msg set batch_id = batchID where batch_id <> batchID;
update retrieve_task set batch_id = queue_msg.batch_id
  from queue_msg
  where queue_msg_fk = queue_msg.pk and retrieve_task.batch_id <> queue_msg.batch_id;

-- part 3: can be applied on already running archive 5.17
alter table queue_msg drop batchID;
