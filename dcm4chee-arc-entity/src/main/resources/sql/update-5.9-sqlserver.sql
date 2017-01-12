create table hl7psu_task (pk bigint identity not null, aet varchar(255) not null, created_time datetime2 not null, device_name varchar(255) not null, scheduled_time datetime2, study_iuid varchar(255), mpps_fk bigint, primary key (pk));
alter table hl7psu_task add constraint UK_p5fraoqdbaywmlyumaeo16t56  unique (study_iuid);
create index UK_t0y05h07d9dagn9a4a9s4a5a4 on hl7psu_task (device_name);
alter table hl7psu_task add constraint FK_pev4urgkk7id2h1ijhv8domjx foreign key (mpps_fk) references mpps;
create index FK_pev4urgkk7id2h1ijhv8domjx on hl7psu_task (mpps_fk);
create sequence hl7psu_task_pk_seq;
