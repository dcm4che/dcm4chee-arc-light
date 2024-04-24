-- part 1: can be applied on archive running archive 5.23
create table task (
    pk number(19,0) not null,
    batch_id varchar2(255 char),
    check_different number(1,0),
    check_missing number(1,0),
    compare_fields varchar2(255 char),
    completed number(10,0),
    created_time timestamp not null,
    destination_aet varchar2(255 char),
    device_name varchar2(255 char) not null,
    different number(10,0) not null,
    error_comment varchar2(255 char),
    error_msg varchar2(255 char),
    exporter_id varchar2(255 char),
    failed number(10,0),
    local_aet varchar2(255 char),
    matches number(10,0) not null,
    missing number(10,0) not null,
    modalities varchar2(255 char),
    num_failures number(10,0) not null,
    num_instances number(10,0),
    outcome_msg varchar2(255 char),
    payload blob,
    proc_end_time timestamp,
    proc_start_time timestamp,
    query_str varchar2(255 char),
    queue_name varchar2(255 char) not null,
    remaining number(10,0),
    remote_aet varchar2(255 char),
    rq_uri varchar2(4000 char),
    rq_host varchar2(255 char),
    rq_user_id varchar2(255 char),
    scheduled_time timestamp not null,
    series_iuid varchar2(255 char),
    sop_iuid varchar2(255 char),
    task_status number(10,0) not null,
    status_code number(10,0),
    storage_ids varchar2(255 char),
    stgcmt_policy number(10,0),
    study_iuid varchar2(255 char),
    task_type number(10,0) not null,
    update_location_status number(1,0),
    updated_time timestamp not null,
    version number(19,0),
    warning number(10,0) not null,
    primary key (pk)
);

create table rel_task_dicomattrs (task_fk number(19,0) not null, dicomattrs_fk number(19,0) not null);

alter table rel_task_dicomattrs add constraint UK_e0gtunmen48q8imxggunt7gt7  unique (dicomattrs_fk);
alter table rel_task_dicomattrs add constraint FK_e0gtunmen48q8imxggunt7gt7 foreign key (dicomattrs_fk) references dicomattrs;
alter table rel_task_dicomattrs add constraint FK_pwaoih2f4ay4c00avvt79de7h foreign key (task_fk) references task;

alter table stgcmt_result add task_fk number(19,0);


create index UK_j292rvji1d7hintidhgkkcbpw on stgcmt_result (task_fk);
create index UK_m47ruxpag7pq4gtn12lc63yfe on task (device_name);
create index UK_r2bcfyreh4n9h392iik1aa6sh on task (queue_name);
create index UK_a582by7kuyuhk8hi41tkelhrw on task (task_type);
create index UK_7y5ucdiygunyg2nh7qrs70e7k on task (task_status);
create index UK_76hkd9mjludoohse4g0ru1mg8 on task (created_time);
create index UK_9htwq4ofarp6m88r3ao0grt8j on task (updated_time);
create index UK_xwqht1afwe7k27iulvggnwwl on task (scheduled_time);
create index UK_k6dxmm1gu6u23xq03hbk80m4r on task (batch_id);
create index UK_17gcm1xo6fkujauguyjfxfb2k on task (local_aet);
create index UK_81xi6wnv5b10x3723fxt5bmew on task (remote_aet);
create index UK_f7c43c242ybnvcn3o50lrcpkh on task (destination_aet);
create index UK_pknlk8ggf8lnq38lq3gacvvpt on task (check_missing);
create index UK_1lchdfbbwkjbg7a6coy5t8iq7 on task (check_different);
create index UK_ow0nufrtniev7nkh7d0uv5mxe on task (compare_fields);
create index UK_6a0y0rsssms4mtm9bpkw8vgl6 on task (study_iuid, series_iuid, sop_iuid);

create index FK_pwaoih2f4ay4c00avvt79de7h on rel_task_dicomattrs (task_fk) ;

create sequence task_pk_seq;
-- part 2: shall be applied on stopped archive before starting 5.24

-- part 3: can be applied on already running archive 5.24
alter table stgcmt_result drop column msg_id;

drop table diff_task_attrs;
drop table diff_task;
drop table export_task;
drop table retrieve_task;
drop table stgver_task;
drop table queue_msg;

drop sequence diff_task_pk_seq;
drop sequence export_task_pk_seq;
drop sequence retrieve_task_pk_seq;
drop sequence stgver_task_pk_seq;
drop sequence queue_msg_pk_seq;