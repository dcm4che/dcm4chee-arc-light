-- part 1: can be applied on archive running archive 5.18
create table rel_workitem_perf_code (
    workitem_fk int8 not null,
    perf_code_fk int8 not null);

create table workitem (
    pk int8 not null,
    admission_id varchar(255) not null,
    created_time timestamp not null,
    expected_end_date_time varchar(255) not null,
    input_readiness_state int4 not null,
    ups_state int4 not null,
    replaced_iuid varchar(255) not null,
    sps_expiration_date_time varchar(255) not null,
    sps_start_date_time varchar(255) not null,
    sop_iuid varchar(255) not null,
    sps_label varchar(255) not null,
    sps_priority int4 not null,
    transaction_iuid varchar(255),
    updated_time timestamp not null,
    version int8,
    worklist_label varchar(255) not null,
    dicomattrs_fk int8 not null,
    admid_issuer_fk int8,
    patient_fk int8 not null,
    station_class_fk int8,
    station_location_fk int8,
    station_name_fk int8,
    sps_code_fk int8,
    primary key (pk));

create table workitem_req (
    pk int8 not null,
    accession_no varchar(255) not null,
    req_proc_id varchar(255) not null,
    req_service varchar(255) not null,
    study_iuid varchar(255) not null,
    accno_issuer_fk int8,
    req_phys_name_fk int8,
    workitem_fk int8,
    primary key (pk));

alter table rel_workitem_perf_code add constraint FK_idrnml8s7gdgs04fefuf5f8h4 foreign key (perf_code_fk) references code;
alter table rel_workitem_perf_code add constraint FK_8g3s4065mf5w2qc1ke71bics0 foreign key (workitem_fk) references workitem;

alter table workitem add constraint UK_ymhqn60mmlk5neh49ba7sxy0  unique (dicomattrs_fk);
alter table workitem add constraint UK_qy5pmj9p4spj7iwjhbh4hl9eq  unique (sop_iuid);
alter table workitem add constraint FK_ymhqn60mmlk5neh49ba7sxy0 foreign key (dicomattrs_fk) references dicomattrs;
alter table workitem add constraint FK_fvr0yj26cll6stqorf8cjb90e foreign key (admid_issuer_fk) references issuer;
alter table workitem add constraint FK_q2810xq8kodn16416t7wmpe7e foreign key (patient_fk) references patient;
alter table workitem add constraint FK_3mybk75p5pg52e7dhf30llgr2 foreign key (station_class_fk) references code;
alter table workitem add constraint FK_rl2yjyeglg18q9r0p775yb7iv foreign key (station_location_fk) references code;
alter table workitem add constraint FK_x15we70ujok7n2n2rw67yd3q foreign key (station_name_fk) references code;
alter table workitem add constraint FK_9q0051yui2061jcc08f0gfrtu foreign key (sps_code_fk) references code;

alter table workitem_req add constraint FK_69wnpgad3croyfw6i62qccq21 foreign key (accno_issuer_fk) references issuer;
alter table workitem_req add constraint FK_1n56hh6qf917v7278105yh677 foreign key (req_phys_name_fk) references person_name;
alter table workitem_req add constraint FK_9m9872gb745depwhavs4yyhpx foreign key (workitem_fk) references workitem;

create index UK_5m7lteyqgfu6csd5tta312e1w on workitem (updated_time);
create index UK_35pndldtlcc0yfp266hlavycd on workitem (sps_priority);
create index UK_q9s35d7kenr5wvh40ev3vxklc on workitem (sps_label);
create index UK_kmpgllryaharjp4p6ctarkgqk on workitem (worklist_label);
create index UK_qq8glucvv6375miuy3wolfw1f on workitem (sps_start_date_time);
create index UK_2tm64abxbih0dpgjwtewikeon on workitem (sps_expiration_date_time);
create index UK_p544xsxks4vcl5f6bvxenguh8 on workitem (expected_end_date_time);
create index UK_a73d0irfjvnqfajdwp59ok99l on workitem (input_readiness_state);
create index UK_mmq6fekut664xsvhrviscktp0 on workitem (admission_id);
create index UK_pabt1p96esjqajt2jfl5j8pg5 on workitem (replaced_iuid);
create index UK_lftm6mfr3iyvnhamdmenxy0g8 on workitem (ups_state);

create index UK_k0m5wgtpi8maqwk9sgff09psq on workitem_req (accession_no);
create index UK_svh7ueh54cjkag0p6spcf7ygw on workitem_req (req_service);
create index UK_o6l0kbqufob8nk2g2874tdmb0 on workitem_req (req_proc_id);
create index UK_qac34nek81rs9kgj7l92osv6g on workitem_req (study_iuid);

create index FK_idrnml8s7gdgs04fefuf5f8h4 on rel_workitem_perf_code (perf_code_fk) ;
create index FK_8g3s4065mf5w2qc1ke71bics0 on rel_workitem_perf_code (workitem_fk) ;

create index FK_fvr0yj26cll6stqorf8cjb90e on workitem (admid_issuer_fk) ;
create index FK_q2810xq8kodn16416t7wmpe7e on workitem (patient_fk) ;
create index FK_3mybk75p5pg52e7dhf30llgr2 on workitem (station_class_fk) ;
create index FK_rl2yjyeglg18q9r0p775yb7iv on workitem (station_location_fk) ;
create index FK_x15we70ujok7n2n2rw67yd3q on workitem (station_name_fk) ;
create index FK_9q0051yui2061jcc08f0gfrtu on workitem (sps_code_fk) ;

create index FK_69wnpgad3croyfw6i62qccq21 on workitem_req (accno_issuer_fk) ;
create index FK_1n56hh6qf917v7278105yh677 on workitem_req (req_phys_name_fk) ;
create index FK_9m9872gb745depwhavs4yyhpx on workitem_req (workitem_fk) ;

create sequence workitem_pk_seq;
create sequence workitem_request_pk_seq;

-- part 2: shall be applied on stopped archive before starting 5.19

-- part 3: can be applied on already running archive 5.19
