-- part 1: can be applied on archive running archive 5.20
alter table retrieve_task
    add scheduled_time timestamp;

alter table export_task
    alter scheduled_time drop not null;

create index UK_rqp93vxrhyg09x3ck7vc1mawp on retrieve_task (scheduled_time);

-- part 2: shall be applied on stopped archive before starting 5.21
update retrieve_task set scheduled_time=queue_msg.scheduled_time from queue_msg where queue_msg_fk=queue_msg.pk;

-- part 3: can be applied on already running archive 5.21
