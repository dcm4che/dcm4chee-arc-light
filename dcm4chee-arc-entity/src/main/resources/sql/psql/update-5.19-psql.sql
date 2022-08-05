-- part 1: can be applied on archive running archive 5.18
create table global_subscription (
    pk int8 not null,
    deletion_lock boolean not null,
    subscriber_aet varchar(255) not null,
    matchkeys_fk int8,
    primary key (pk));

create table rel_ups_perf_code (
    ups_fk int8 not null,
    perf_code_fk int8 not null);

create table subscription (
    pk int8 not null,
    deletion_lock boolean not null,
    subscriber_aet varchar(255) not null,
    ups_fk int8 not null,
    primary key (pk));

create table ups (
    pk int8 not null,
    admission_id varchar(255) not null,
    created_time timestamp not null,
    expected_end_date_time varchar(255) not null,
    input_readiness_state int4 not null,
    performer_aet varchar(255),
    ups_state int4 not null,
    replaced_iuid varchar(255) not null,
    expiration_date_time varchar(255) not null,
    start_date_time varchar(255) not null,
    transaction_iuid varchar(255),
    updated_time timestamp not null,
    ups_iuid varchar(255) not null,
    ups_label varchar(255) not null,
    ups_priority int4 not null,
    version int8,
    worklist_label varchar(255) not null,
    dicomattrs_fk int8 not null,
    admission_issuer_fk int8,
    patient_fk int8 not null,
    station_class_fk int8,
    station_location_fk int8,
    station_name_fk int8,
    ups_code_fk int8,
    primary key (pk));

create table ups_req (
    pk int8 not null,
    accession_no varchar(255) not null,
    req_proc_id varchar(255) not null,
    req_service varchar(255) not null,
    study_iuid varchar(255) not null,
    accno_issuer_fk int8,
    req_phys_name_fk int8,
    ups_fk int8,
    primary key (pk));

alter table global_subscription add constraint UK_4n26cxir6d3tksb2cd1kd86ch  unique (subscriber_aet);
alter table subscription add constraint UK_co8q5hn46dehb35qsrtwyys96  unique (subscriber_aet, ups_fk);
alter table ups add constraint UK_3frtpy5cstsoxk5jxw9cutr33  unique (dicomattrs_fk);
alter table ups add constraint UK_qck03rlxht9myv77sc79a480t  unique (ups_iuid);

create index UK_1umoxe7ig9n21q885mncxeq9 on ups (updated_time);
create index UK_kgwfwmxj3i0n7c404uvhsav1g on ups (ups_priority);
create index UK_d3ejkrtcim0q3cbwpq4n9skes on ups (ups_label);
create index UK_7e44lxlg0m2l2wfdo3k2tec7o on ups (worklist_label);
create index UK_kh194du6g35fi5l80vbj18nnp on ups (start_date_time);
create index UK_e57ifctiig366oq9mhab8law3 on ups (expiration_date_time);
create index UK_1hdr3ml1rwugwkmo3eks4no5p on ups (expected_end_date_time);
create index UK_brtgc3vpnoaq1xm80m568r16y on ups (input_readiness_state);
create index UK_sqoo5rr8pu2qe4gtdne3xh031 on ups (admission_id);
create index UK_crl67piqoxiccp3i6ckktphdd on ups (replaced_iuid);
create index UK_c8obxmqpdcy37r3pjga2pukac on ups (ups_state);
create index UK_rfium2ybikqm1f4xmi24mnv4u on ups_req (accession_no);
create index UK_emsk27nclko11ph2tcj5vk7hg on ups_req (req_service);
create index UK_524vr0q4c0kvyjwov74eru44x on ups_req (req_proc_id);
create index UK_hf0tly8umknn77civcsi0tdih on ups_req (study_iuid);

alter table global_subscription add constraint FK_f1l196ykcnh7s2pwo6qnmltw7 foreign key (matchkeys_fk) references dicomattrs;
alter table rel_ups_perf_code add constraint FK_6asj28yy5se9mp443b6ryefd2 foreign key (perf_code_fk) references code;
alter table rel_ups_perf_code add constraint FK_6m06tt8ku376qxkro94xpteus foreign key (ups_fk) references ups;
alter table subscription add constraint FK_jadcs2aho4ijh639r67qgk0g0 foreign key (ups_fk) references ups;
alter table ups add constraint FK_3frtpy5cstsoxk5jxw9cutr33 foreign key (dicomattrs_fk) references dicomattrs;
alter table ups add constraint FK_61tpdp9aoy98jwiif5wq82ia3 foreign key (admission_issuer_fk) references issuer;
alter table ups add constraint FK_8xiqdli1p8cyw1y4hwyqhimcx foreign key (patient_fk) references patient;
alter table ups add constraint FK_ak183xmw0sai4jg9lib6m14o2 foreign key (station_class_fk) references code;
alter table ups add constraint FK_ox3hpmd042ywnww3yh33crcoj foreign key (station_location_fk) references code;
alter table ups add constraint FK_gd2hu9idxg6rd71g1i8r8wyjr foreign key (station_name_fk) references code;
alter table ups add constraint FK_1retecpk22a2tysmi5o6xcpbh foreign key (ups_code_fk) references code;
alter table ups_req add constraint FK_gegm1c1ymem7tj2wcm0o7e0pu foreign key (accno_issuer_fk) references issuer;
alter table ups_req add constraint FK_kocdb2pxx2fymu1modhneb4xm foreign key (req_phys_name_fk) references person_name;
alter table ups_req add constraint FK_7vt6m05r0hertks2fcngd5wn1 foreign key (ups_fk) references ups;

create index FK_f1l196ykcnh7s2pwo6qnmltw7 on global_subscription (matchkeys_fk) ;
create index FK_6asj28yy5se9mp443b6ryefd2 on rel_ups_perf_code (perf_code_fk) ;
create index FK_6m06tt8ku376qxkro94xpteus on rel_ups_perf_code (ups_fk) ;
create index FK_jadcs2aho4ijh639r67qgk0g0 on subscription (ups_fk) ;
create index FK_61tpdp9aoy98jwiif5wq82ia3 on ups (admission_issuer_fk) ;
create index FK_8xiqdli1p8cyw1y4hwyqhimcx on ups (patient_fk) ;
create index FK_ak183xmw0sai4jg9lib6m14o2 on ups (station_class_fk) ;
create index FK_ox3hpmd042ywnww3yh33crcoj on ups (station_location_fk) ;
create index FK_gd2hu9idxg6rd71g1i8r8wyjr on ups (station_name_fk) ;
create index FK_1retecpk22a2tysmi5o6xcpbh on ups (ups_code_fk) ;
create index FK_gegm1c1ymem7tj2wcm0o7e0pu on ups_req (accno_issuer_fk) ;
create index FK_kocdb2pxx2fymu1modhneb4xm on ups_req (req_phys_name_fk) ;
create index FK_7vt6m05r0hertks2fcngd5wn1 on ups_req (ups_fk) ;

create sequence global_subscription_pk_seq;
create sequence subscription_pk_seq;
create sequence ups_pk_seq;
create sequence ups_request_pk_seq;

-- part 2: shall be applied on stopped archive before starting 5.19

-- part 3: can be applied on already running archive 5.19
