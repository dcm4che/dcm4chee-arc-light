alter table queue_msg add batchID varchar(255);
create index UK_2rbj4jw6ffs0ytec06ebv5nld on queue_msg (batchID);