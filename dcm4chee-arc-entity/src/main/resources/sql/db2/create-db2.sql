create table code (pk bigint generated by default as identity, code_meaning varchar(255) not null, code_value varchar(255) not null, code_designator varchar(255) not null, code_version varchar(255) not null, primary key (pk));
create table content_item (pk bigint generated by default as identity, rel_type varchar(255) not null, text_value varchar(255), code_fk bigint, name_fk bigint not null, instance_fk bigint, primary key (pk));
create table dicomattrs (pk bigint generated by default as identity, attrs blob(16K) not null, primary key (pk));
create table global_subscription (pk bigint generated by default as identity, deletion_lock smallint not null, subscriber_aet varchar(255) not null, matchkeys_fk bigint, primary key (pk));
create table hl7psu_task (pk bigint generated by default as identity, accession_no varchar(255), aet varchar(255) not null, created_time timestamp(6) not null, device_name varchar(255) not null, pps_status smallint check (pps_status between 0 and 2), scheduled_time timestamp(6), series_iuid varchar(255), study_iuid varchar(255), mpps_fk bigint, primary key (pk));
create table ian_task (pk bigint generated by default as identity, calling_aet varchar(255) not null, device_name varchar(255) not null, ian_dests varchar(255) not null, scheduled_time timestamp(6), study_iuid varchar(255), mpps_fk bigint, primary key (pk));
create table id_sequence (name varchar(255) not null, next_value integer not null, version bigint, primary key (name));
create table instance (pk bigint generated by default as identity, availability smallint not null check (availability between 0 and 3), sr_complete varchar(255) not null, content_date varchar(255) not null, content_time varchar(255) not null, created_time timestamp(6) not null, ext_retrieve_aet varchar(255) not null, inst_custom1 varchar(255) not null, inst_custom2 varchar(255) not null, inst_custom3 varchar(255) not null, inst_no integer, num_frames integer, retrieve_aets varchar(255), sop_cuid varchar(255) not null, sop_iuid varchar(255) not null, updated_time timestamp(6) not null, sr_verified varchar(255) not null, version bigint, dicomattrs_fk bigint not null, srcode_fk bigint, series_fk bigint not null, primary key (pk));
create table instance_req (pk bigint generated by default as identity, accession_no varchar(255) not null, accno_entity_id varchar(255), accno_entity_uid varchar(255), accno_entity_uid_type varchar(255), req_proc_id varchar(255) not null, req_service varchar(255) not null, sps_id varchar(255) not null, study_iuid varchar(255) not null, req_phys_name_fk bigint, instance_fk bigint, primary key (pk));
create table key_value2 (pk bigint generated by default as identity, content_type varchar(255) not null, created_time timestamp(6) not null, key_name varchar(255) not null, updated_time timestamp(6) not null, username varchar(255), key_value varchar(4000) not null, primary key (pk));
create table location (pk bigint generated by default as identity, created_time timestamp(6) not null, digest varchar(255), multi_ref integer, object_type smallint not null check (object_type between 0 and 1), object_size bigint not null, status smallint not null check (status between 0 and 17), storage_id varchar(255) not null, storage_path varchar(255) not null, tsuid varchar(255), uidmap_fk bigint, instance_fk bigint, primary key (pk));
create table metadata (pk bigint generated by default as identity, created_time timestamp(6) not null, digest varchar(255), object_size bigint not null, status smallint not null check (status between 0 and 9), storage_id varchar(255) not null, storage_path varchar(255) not null, primary key (pk));
create table mpps (pk bigint generated by default as identity, accession_no varchar(255) not null, accno_entity_id varchar(255), accno_entity_uid varchar(255), accno_entity_uid_type varchar(255), created_time timestamp(6) not null, pps_start_date varchar(255) not null, pps_start_time varchar(255) not null, sop_iuid varchar(255) not null, pps_status smallint not null check (pps_status between 0 and 2), study_iuid varchar(255) not null, updated_time timestamp(6) not null, version bigint, dicomattrs_fk bigint not null, discreason_code_fk bigint, patient_fk bigint not null, primary key (pk));
create table mwl_item (pk bigint generated by default as identity, accession_no varchar(255) not null, accno_entity_id varchar(255), accno_entity_uid varchar(255), accno_entity_uid_type varchar(255), admission_id varchar(255) not null, admid_entity_id varchar(255), admid_entity_uid varchar(255), admid_entity_uid_type varchar(255), created_time timestamp(6) not null, institution varchar(255) not null, department varchar(255) not null, modality varchar(255) not null, req_proc_id varchar(255) not null, sps_id varchar(255) not null, sps_start_date varchar(255) not null, sps_start_time varchar(255) not null, sps_status smallint not null check (sps_status between 0 and 7), study_iuid varchar(255) not null, updated_time timestamp(6) not null, version bigint, worklist_label varchar(255) not null, dicomattrs_fk bigint not null, inst_code_fk bigint, dept_code_fk bigint, patient_fk bigint not null, perf_phys_name_fk bigint, primary key (pk));
create table patient (pk bigint generated by default as identity, created_time timestamp(6) not null, failed_verifications integer not null, num_studies integer not null, pat_birthdate varchar(255) not null, pat_custom1 varchar(255) not null, pat_custom2 varchar(255) not null, pat_custom3 varchar(255) not null, pat_sex varchar(255) not null, updated_time timestamp(6) not null, verification_status smallint not null check (verification_status between 0 and 4), verification_time timestamp(6), version bigint, dicomattrs_fk bigint not null, merge_fk bigint, pat_name_fk bigint, resp_person_fk bigint, primary key (pk));
create table patient_demographics (pat_id varchar(255) not null, pat_birthdate varchar(255), pat_name varchar(255), pat_sex varchar(255), primary key (pat_id));
create table patient_id (pk bigint generated by default as identity, pat_id varchar(255) not null, pat_id_type_code varchar(255), entity_id varchar(255) not null, entity_uid varchar(255) not null, entity_uid_type varchar(255) not null, version bigint, patient_fk bigint not null, primary key (pk));
create table person_name (pk bigint generated by default as identity, alphabetic_name varchar(255) not null, ideographic_name varchar(255) not null, phonetic_name varchar(255) not null, primary key (pk));
create table rejected_instance (pk bigint generated by default as identity, created_time timestamp(6) not null, series_iuid varchar(255) not null, sop_cuid varchar(255) not null, sop_iuid varchar(255) not null, study_iuid varchar(255) not null, reject_code_fk bigint, primary key (pk));
create table rel_study_pcode (study_fk bigint not null, pcode_fk bigint not null);
create table rel_task_dicomattrs (task_fk bigint not null, dicomattrs_fk bigint not null);
create table rel_ups_perf_code (ups_fk bigint not null, perf_code_fk bigint not null);
create table rel_ups_station_class_code (ups_fk bigint not null, station_class_code_fk bigint not null);
create table rel_ups_station_location_code (ups_fk bigint not null, station_location_code_fk bigint not null);
create table rel_ups_station_name_code (ups_fk bigint not null, station_name_code_fk bigint not null);
create table series (pk bigint generated by default as identity, access_control_id varchar(255) not null, body_part varchar(255) not null, completeness smallint not null check (completeness between 0 and 2), compress_failures integer not null, compress_params varchar(255), compress_time timestamp(6), compress_tsuid varchar(255), created_time timestamp(6) not null, expiration_date varchar(255), expiration_exporter_id varchar(255), expiration_state smallint not null check (expiration_state between 0 and 5), ext_retrieve_aet varchar(255) not null, failed_retrieves integer not null, stgver_failures integer not null, inst_purge_state smallint not null check (inst_purge_state between 0 and 2), inst_purge_time timestamp(6), institution varchar(255) not null, department varchar(255) not null, laterality varchar(255) not null, metadata_update_time timestamp(6), metadata_update_failures integer not null, modality varchar(255) not null, modified_time timestamp(6) not null, pps_cuid varchar(255) not null, pps_iuid varchar(255) not null, pps_start_date varchar(255) not null, pps_start_time varchar(255) not null, receiving_aet varchar(255), receiving_hl7_app varchar(255), receiving_hl7_facility varchar(255), receiving_pres_addr varchar(255), rejection_state smallint not null check (rejection_state between 0 and 3), sending_aet varchar(255), sending_hl7_app varchar(255), sending_hl7_facility varchar(255), sending_pres_addr varchar(255), series_custom1 varchar(255) not null, series_custom2 varchar(255) not null, series_custom3 varchar(255) not null, series_desc varchar(255) not null, series_iuid varchar(255) not null, series_no integer, series_size bigint not null, sop_cuid varchar(255) not null, station_name varchar(255) not null, stgver_time timestamp(6), tsuid varchar(255) not null, updated_time timestamp(6) not null, version bigint, dicomattrs_fk bigint not null, inst_code_fk bigint, dept_code_fk bigint, metadata_fk bigint, perf_phys_name_fk bigint, study_fk bigint not null, primary key (pk));
create table series_query_attrs (pk bigint generated by default as identity, availability smallint check (availability between 0 and 3), num_instances integer, retrieve_aets varchar(255), cuids_in_series varchar(255), view_id varchar(255), series_fk bigint not null, primary key (pk));
create table series_req (pk bigint generated by default as identity, accession_no varchar(255) not null, accno_entity_id varchar(255), accno_entity_uid varchar(255), accno_entity_uid_type varchar(255), req_proc_id varchar(255) not null, req_service varchar(255) not null, sps_id varchar(255) not null, study_iuid varchar(255) not null, req_phys_name_fk bigint, series_fk bigint, primary key (pk));
create table soundex_code (pk bigint generated by default as identity, sx_code_value varchar(255) not null, sx_pn_comp_part integer not null, sx_pn_comp smallint not null check (sx_pn_comp between 0 and 4), person_name_fk bigint not null, primary key (pk));
create table sps_station_aet (mwl_item_fk bigint not null, station_aet varchar(255));
create table stgcmt_result (pk bigint generated by default as identity, batch_id varchar(255), created_time timestamp(6) not null, device_name varchar(255) not null, exporter_id varchar(255), num_failures integer, num_instances integer, series_iuid varchar(255), sop_iuid varchar(255), stgcmt_status smallint not null check (stgcmt_status between 0 and 3), study_iuid varchar(255) not null, task_fk bigint, transaction_uid varchar(255) not null, updated_time timestamp(6) not null, primary key (pk));
create table study (pk bigint generated by default as identity, access_control_id varchar(255) not null, access_time timestamp(6) not null, accession_no varchar(255) not null, accno_entity_id varchar(255), accno_entity_uid varchar(255), accno_entity_uid_type varchar(255), admission_id varchar(255) not null, admid_entity_id varchar(255), admid_entity_uid varchar(255), admid_entity_uid_type varchar(255), completeness smallint not null check (completeness between 0 and 2), created_time timestamp(6) not null, study_deleting smallint not null, expiration_date varchar(255), expiration_exporter_id varchar(255), expiration_state smallint not null check (expiration_state between 0 and 5), ext_retrieve_aet varchar(255) not null, failed_retrieves integer not null, modified_time timestamp(6) not null, rejection_state smallint not null check (rejection_state between 0 and 3), study_size bigint not null, storage_ids varchar(255), study_custom1 varchar(255) not null, study_custom2 varchar(255) not null, study_custom3 varchar(255) not null, study_date varchar(255) not null, study_desc varchar(255) not null, study_id varchar(255) not null, study_iuid varchar(255) not null, study_time varchar(255) not null, updated_time timestamp(6) not null, version bigint, dicomattrs_fk bigint not null, patient_fk bigint not null, ref_phys_name_fk bigint, primary key (pk));
create table study_query_attrs (pk bigint generated by default as identity, availability smallint check (availability between 0 and 3), mods_in_study varchar(255), num_instances integer, num_series integer, retrieve_aets varchar(255), cuids_in_study varchar(4000), view_id varchar(255), study_fk bigint not null, primary key (pk));
create table subscription (pk bigint generated by default as identity, deletion_lock smallint not null, subscriber_aet varchar(255) not null, ups_fk bigint not null, primary key (pk));
create table task (pk bigint generated by default as identity, batch_id varchar(255), check_different smallint, check_missing smallint, compare_fields varchar(255), completed integer, created_time timestamp(6) not null, destination_aet varchar(255), device_name varchar(255) not null, different integer not null, error_comment varchar(255), error_msg varchar(255), exporter_id varchar(255), failed integer, local_aet varchar(255), matches integer not null, missing integer not null, modalities varchar(255), num_failures integer not null, num_instances integer, outcome_msg varchar(255), payload blob(16K), proc_end_time timestamp(6), proc_start_time timestamp(6), query_str varchar(255), queue_name varchar(255) not null, remaining integer, remote_aet varchar(255), rq_uri varchar(4000), rq_host varchar(255), rq_user_id varchar(255), scheduled_time timestamp(6) not null, series_iuid varchar(255), sop_iuid varchar(255), task_status smallint not null check (task_status between 0 and 6), status_code integer, storage_ids varchar(255), stgcmt_policy smallint check (stgcmt_policy between 0 and 5), study_iuid varchar(255), task_type smallint not null check (task_type between 0 and 10), update_location_status smallint, updated_time timestamp(6) not null, version bigint, warning integer not null, primary key (pk));
create table uidmap (pk bigint generated by default as identity, uidmap blob(16K) not null, primary key (pk));
create table ups (pk bigint generated by default as identity, admission_id varchar(255) not null, admid_entity_id varchar(255), admid_entity_uid varchar(255), admid_entity_uid_type varchar(255), created_time timestamp(6) not null, expected_end_date_time varchar(255) not null, input_readiness_state smallint not null check (input_readiness_state between 0 and 2), performer_aet varchar(255), ups_state smallint not null check (ups_state between 0 and 3), replaced_iuid varchar(255) not null, expiration_date_time varchar(255) not null, start_date_time varchar(255) not null, transaction_iuid varchar(255), updated_time timestamp(6) not null, ups_iuid varchar(255) not null, ups_label varchar(255) not null, ups_priority smallint not null check (ups_priority between 0 and 2), version bigint, worklist_label varchar(255) not null, dicomattrs_fk bigint not null, perf_name_fk bigint, patient_fk bigint not null, ups_code_fk bigint, primary key (pk));
create table ups_req (pk bigint generated by default as identity, accession_no varchar(255) not null, accno_entity_id varchar(255), accno_entity_uid varchar(255), accno_entity_uid_type varchar(255), req_proc_id varchar(255) not null, req_service varchar(255) not null, study_iuid varchar(255) not null, req_phys_name_fk bigint, ups_fk bigint, primary key (pk));
create table verify_observer (pk bigint generated by default as identity, verify_datetime varchar(255) not null, observer_name_fk bigint, instance_fk bigint, primary key (pk));
alter table code add constraint UKsb4oc9lkns36wswku831c33w6 unique (code_value, code_designator, code_version);
create index IDXi715nk4mi378f9bxflvfroa5a on content_item (rel_type);
create index IDX6iism30y000w85v649ju968sv on content_item (text_value);
alter table global_subscription add constraint UK4n26cxir6d3tksb2cd1kd86ch unique (subscriber_aet);
create unique index UKf1l196ykcnh7s2pwo6qnmltw7 on global_subscription (matchkeys_fk) exclude null keys;
create index IDXt0y05h07d9dagn9a4a9s4a5a4 on hl7psu_task (device_name);
create unique index UK1t3jge4o2fl1byp3y8ljmkb3m on hl7psu_task (study_iuid, pps_status) exclude null keys;
create unique index UKpev4urgkk7id2h1ijhv8domjx on hl7psu_task (mpps_fk) exclude null keys;
create index IDX5shiir23exao1xpy2n5gvasrh on ian_task (device_name);
create unique index UKdq88edcjjxh7h92f89y5ueast on ian_task (study_iuid) exclude null keys;
create unique index UK1fuh251le2hid2byw90hd1mly on ian_task (mpps_fk) exclude null keys;
create index IDXeg0khesxr81gdimwhjiyrylw7 on instance (sop_iuid);
create index IDXdglm8ndp9y9i0uo6fgaa5rhbb on instance (sop_cuid);
create index IDXouh6caecancvsa05lknojy30j on instance (inst_no);
create index IDX5ikkfk17vijvsvtyied2xa225 on instance (content_date);
create index IDXpck1ovyd4t96mjkbbw6f8jiam on instance (content_time);
create index IDXqh8jqpe8ulsb5t7iv24scho00 on instance (sr_verified);
create index IDXgisd09x31lphi4437hwgh7ihg on instance (sr_complete);
create index IDXfncb1s641rrnoek7j47k0j06n on instance (inst_custom1);
create index IDXrr1ro1oxv6s4riib9hjkcuvuo on instance (inst_custom2);
create index IDXq5i0hxt1iyahxjiroux2h8imm on instance (inst_custom3);
alter table instance add constraint UK247lgirehl8i2vuanyfjnuyjb unique (series_fk, sop_iuid);
alter table instance add constraint UKjxfu47kwjk3kkkyrwewjw8a4n unique (dicomattrs_fk);
create index IDXcqpv94ky100d0eguhrxpyplmv on instance_req (accession_no);
create index IDXn32ktg5h9xc1ex9x8g69w1s10 on instance_req (req_service);
create index IDX7pudwdgrg9wwc73wo65hpg517 on instance_req (req_proc_id);
create index IDX43h9ogidkcnex0e14q6u0c3jn on instance_req (sps_id);
create index IDX1typgaxhn4d0pt1f0vlp18wvb on instance_req (study_iuid);
create unique index UKcqmmps9maltjybl44t4cck404 on instance_req (req_phys_name_fk) exclude null keys;
create index IDXhy6xxbt6wi79kbxt6wsqhv77p on key_value2 (username);
create index IDX5gcbr7nnck6dxrmml1s3arpna on key_value2 (updated_time);
alter table key_value2 add constraint UK4gq7ksl296rsm6ap9hjrogv3g unique (key_name);
create index IDXr3oh859i9osv3aluoc8dcx9wk on location (storage_id, status);
create index IDXi1lnahmehau3r3j9pdyxg3p3y on location (multi_ref);
create index IDXf7c9hmq8pfypohkgkp5vkbhxp on metadata (storage_id, status);
alter table mpps add constraint UKcyqglxijg7kebbj6oj821yx4d unique (sop_iuid);
alter table mpps add constraint UKo49fec996jvdo31o7ysmsn9s2 unique (dicomattrs_fk);
create index IDXd0v5hjn1crha2nqbws4wj0yoj on mwl_item (updated_time);
create index IDX88bqeff7thxsmgcmtrg5l3td on mwl_item (worklist_label);
create index IDX2odo3oah39o400thy9bf0rgv0 on mwl_item (sps_id);
create index IDXkedi0qimmvs83af3jxk471uxn on mwl_item (req_proc_id);
create index IDXfpfq8q514gsime2dl8oo773d4 on mwl_item (study_iuid);
create index IDXpw8h1b4sac2sr9estyqr82pcf on mwl_item (accession_no);
create index IDXtlkw80b7pbutfj19vh6et2vs7 on mwl_item (admission_id);
create index IDX8qkftk7n30hla3v1frep9vg2q on mwl_item (institution);
create index IDXksy3uy0rvpis1sqqeojlet7lb on mwl_item (department);
create index IDXq28149iaxebyt3de2h5sm2bgl on mwl_item (modality);
create index IDX9oh3yd4prp9sfys4n0p2kd69y on mwl_item (sps_start_date);
create index IDXm20xnkg1iqetifvuegehbhekm on mwl_item (sps_start_time);
create index IDX3oigo76r1a7et491bkci96km8 on mwl_item (sps_status);
alter table mwl_item add constraint UKlerlqlaghhcs0oaj5irux4qig unique (study_iuid, sps_id);
alter table mwl_item add constraint UK6qj8tkh6ib9w2pjqwvqe23ko unique (dicomattrs_fk);
create unique index UK44qwwvs50lgpog2cqmicxgt1f on mwl_item (perf_phys_name_fk) exclude null keys;
create index IDXdhohkk791t9yvrlt0lihik992 on patient (created_time);
create index IDXe7rsyrt9n2mccyv1fcd2s6ikv on patient (verification_status);
create index IDXbay8wkvwegw3pmyeypv2v93k1 on patient (verification_time);
create index IDX296rccryifu6d8byisl2f4dvq on patient (num_studies);
create index IDX1ho1jyofty54ip8aqpuhi4mu1 on patient (pat_birthdate);
create index IDX545wp9un24fhgcy2lcfu1o04y on patient (pat_sex);
create index IDX9f2m2lkijm7wi0hpjsime069n on patient (pat_custom1);
create index IDXdwp6no1c4624yii6sbo59fedg on patient (pat_custom2);
create index IDX3ioui3yamjf01yny98bliqfgs on patient (pat_custom3);
alter table patient add constraint UK5lgndn3gn7iug3kuewiy9q124 unique (dicomattrs_fk);
create unique index UKrj42ffdtimnrcwmqqlvj24gi2 on patient (pat_name_fk) exclude null keys;
create unique index UK56r2g5ggptqgcvb3hl11adke2 on patient (resp_person_fk) exclude null keys;
create index IDXtkyjkkxxhnr0fem7m0h3844jk on patient_id (pat_id);
create index IDXd1sdyupb0vwvx23jownjnyy72 on patient_id (entity_id);
create index IDXm2jq6xe87vegohf6g10t5ptew on patient_id (entity_uid, entity_uid_type);
alter table patient_id add constraint UKm3ywheu0gq9baixf5dmrhg6oo unique (pat_id, entity_id, entity_uid, entity_uid_type);
create index IDXgs2yshbwu0gkd33yxyv13keoh on person_name (alphabetic_name);
create index IDXala4l4egord8i2tjvjidoqd1s on person_name (ideographic_name);
create index IDX9nr8ddkp8enufvbn72esyw3n1 on person_name (phonetic_name);
create index IDXowm55at56tdjitsncsrhr93xj on rejected_instance (created_time);
alter table rejected_instance add constraint UK6liqevdmi0spifxf2vrh18wkp unique (study_iuid, series_iuid, sop_iuid);
alter table rel_task_dicomattrs add constraint UKe0gtunmen48q8imxggunt7gt7 unique (dicomattrs_fk);
create index IDX9fi64g5jjycg9dp24jjk5txg1 on series (series_iuid);
create index IDXr9qbr5jv4ejclglvyvtsynuo9 on series (access_control_id);
create index IDXjlgy9ifvqak4g2bxkchismw8x on series (rejection_state);
create index IDX75oc6w5ootkuwyvmrhe3tbown on series (series_no);
create index IDXb126hub0dc1o9dqp6awoispx2 on series (modality);
create index IDXmrn00m45lkq1xbehmbw5d9jbl on series (sop_cuid);
create index IDXtahx0q1ejidnsam40ans7oecx on series (tsuid);
create index IDXpq1yi70ftxhh391lhcq3e08nf on series (station_name);
create index IDXrvlxc150uexwmr1l9ojp8fgd on series (pps_start_date);
create index IDXamr00xwlatxewgj1sjp5mnf76 on series (pps_start_time);
create index IDXgwp46ofa26am9ohhccq1ohdj on series (body_part);
create index IDXtbdrfrmkmifsqhpf43065jrbs on series (laterality);
create index IDXachxn1rtfm3fbkkswlsyr75t0 on series (series_desc);
create index IDX82qea56c0kdhod3b1wu8wbrny on series (institution);
create index IDXbqu32v5v76p4qi0etptnrm0pc on series (department);
create index IDXhm39592a9n7m54dgso17irlhv on series (series_custom1);
create index IDXq3wayt2ke25fdcghaohhrjiu7 on series (series_custom2);
create index IDXd8b8irasiw8eh9tsigmwkbvae on series (series_custom3);
create index IDXb9e2bptvail8xnmb62h30h4d2 on series (sending_aet);
create index IDXlnck3a2qjo1vc430n1sy51vbr on series (receiving_aet);
create index IDXgxun7s005k8qf7qwhjhkkkkng on series (sending_pres_addr);
create index IDXe15a6qnq8jcq931agc2v48nvt on series (receiving_pres_addr);
create index IDXffpftwfkijejj09tlbxr7u5g8 on series (sending_hl7_app);
create index IDX1e4aqxc5w1557hr3fb3lqm2qb on series (sending_hl7_facility);
create index IDXgj0bxgi55bhjic9s3i4dp2aee on series (receiving_hl7_app);
create index IDXpbay159cdhwbtjvlmel6d6em2 on series (receiving_hl7_facility);
create index IDXih49lthl3udoca5opvgsdcerj on series (expiration_state);
create index IDXta3pi6exqft5encv389hwjytw on series (expiration_date);
create index IDXj6aadbh7u93bpmv18s1inrl1r on series (failed_retrieves);
create index IDX4lnegvfs65fbkjn7nmg9s8usy on series (completeness);
create index IDXhwkcpd7yv0nca7o918wm4bn69 on series (metadata_update_time);
create index IDX6xqpk4cvy49wj41p2qwixro8w on series (metadata_update_failures);
create index IDXa8vyikwd972jomyb3f6brcfh5 on series (inst_purge_time);
create index IDXer4ife08f6eaki91gt3hxt5e on series (inst_purge_state);
create index IDXftv3ijh2ud6ogoknneyqc6t9i on series (stgver_time);
create index IDXs1vceb8cu9c45j0q8tbldgol9 on series (stgver_failures);
create index IDX38mfgfnjhan2yhnwqtcrawe4 on series (compress_time);
create index IDX889438ocqfrvybu3k2eo65lpa on series (compress_failures);
alter table series add constraint UK83y2fx8cou17h3xggxspgikna unique (study_fk, series_iuid);
alter table series add constraint UKbdj2kuutidekc2en6dckev7l6 unique (dicomattrs_fk);
create unique index UKpu4p7k1o9hleuk9rmxvw2ybj6 on series (metadata_fk) exclude null keys;
create unique index UK5n4bxxb2xa7bvvq26ao7wihky on series (perf_phys_name_fk) exclude null keys;
create unique index UKt1uhb1suiiqffhsv9eaopeevs on series_query_attrs (view_id, series_fk) exclude null keys;
create index IDXm4wanupyq3yldxgh3pbo7t68h on series_req (accession_no);
create index IDXl1fg3crmk6pjeu1x36e25h6p4 on series_req (req_service);
create index IDXp9w1wg4031w6y66w4xhx1ffay on series_req (req_proc_id);
create index IDX4uq79j30ind90jjs68gb24j6e on series_req (sps_id);
create index IDXcrnpneoalwq25p795xtrhbx2 on series_req (study_iuid);
create unique index UKbcn0jtvurqutw865pwp34pejb on series_req (req_phys_name_fk) exclude null keys;
create index IDXfjwlo6vk0gxp78eh2i7j04a5t on soundex_code (sx_pn_comp);
create index IDXnlv8hnjxmb7pobdfl094ud14u on soundex_code (sx_pn_comp_part);
create index IDX3dxkqfajcytiwjjb5rgh4nu1l on soundex_code (sx_code_value);
create index IDXtm93u8kuxnasoguns5asgdx4a on sps_station_aet (station_aet);
create index IDXqko59fn9pb87j1eu070ilfkhm on stgcmt_result (updated_time);
create index IDX7ltjgxoijy15rrwihl8euv7vh on stgcmt_result (device_name);
create index IDXgu96kxnbf2p84d1katepo0btq on stgcmt_result (exporter_id);
create index IDXj292rvji1d7hintidhgkkcbpw on stgcmt_result (task_fk);
create index IDXf718gnu5js0mdg39q6j7fklia on stgcmt_result (batch_id);
create index IDXp65blcj4h0uh2itb0bp49mc07 on stgcmt_result (study_iuid);
create index IDXnyoefler7agcmxc8t8yfngq7e on stgcmt_result (stgcmt_status);
alter table stgcmt_result add constraint UKey6qpep2qtiwayou7pd0vj22w unique (transaction_uid);
create index IDXq8k2sl3kjl18qg1nr19l47tl1 on study (access_time);
create index IDX6ry2squ4qcv129lxpae1oy93m on study (created_time);
create index IDX24av2ewa70e7cykl340n63aqd on study (access_control_id);
create index IDXhwu9omd369ju3nufufxd3vof2 on study (rejection_state);
create index IDXfypbtohf5skbd3bkyd792a6dt on study (storage_ids);
create index IDXa1rewlmf8uxfgshk25f6uawx2 on study (study_date);
create index IDX16t2xvj9ttyvbwh1ijeve01ii on study (study_time);
create index IDX2ofn5q0fdfc6941e5j34bplmv on study (accession_no);
create index IDXn5froudmhk14pbhgors43xi68 on study (admission_id);
create index IDXj3q7fkhhiu4bolglyve3lv385 on study (study_desc);
create index IDXksy103xef0hokd33y8ux7afxl on study (study_custom1);
create index IDXj63d3ip6x4xslkmyks1l89aay on study (study_custom2);
create index IDX8xolm3oljt08cuheepwq3fls7 on study (study_custom3);
create index IDXfyasyw3wco6hoj2entr7l6d09 on study (expiration_state);
create index IDXmlk5pdi8une92kru8g2ppappx on study (expiration_date);
create index IDX9qvng5j8xnli8yif7p0rjngb2 on study (failed_retrieves);
create index IDXgl5rq54a0tr8nreu27c2t04rb on study (completeness);
create index IDXcl9dmi0kb97ov1cjh7rn3dhve on study (ext_retrieve_aet);
create index IDXq7vxiaj1q6ojfxdq1g9jjxgqv on study (study_size);
alter table study add constraint UKpt5qn20x278wb1f7p2t3lcxv unique (study_iuid);
alter table study add constraint UK5w0oynbw061mwu1rr9mrb6kj4 unique (dicomattrs_fk);
create unique index UK49eet5qgcsb32ktsqrf1mj3x2 on study (ref_phys_name_fk) exclude null keys;
create unique index UKprn4qt6d42stw0gfi1yce1fap on study_query_attrs (view_id, study_fk) exclude null keys;
alter table subscription add constraint UKco8q5hn46dehb35qsrtwyys96 unique (subscriber_aet, ups_fk);
create index IDXm47ruxpag7pq4gtn12lc63yfe on task (device_name);
create index IDXr2bcfyreh4n9h392iik1aa6sh on task (queue_name);
create index IDXa582by7kuyuhk8hi41tkelhrw on task (task_type);
create index IDX7y5ucdiygunyg2nh7qrs70e7k on task (task_status);
create index IDX76hkd9mjludoohse4g0ru1mg8 on task (created_time);
create index IDX9htwq4ofarp6m88r3ao0grt8j on task (updated_time);
create index IDXxwqht1afwe7k27iulvggnwwl on task (scheduled_time);
create index IDXk6dxmm1gu6u23xq03hbk80m4r on task (batch_id);
create index IDX17gcm1xo6fkujauguyjfxfb2k on task (local_aet);
create index IDX81xi6wnv5b10x3723fxt5bmew on task (remote_aet);
create index IDXf7c43c242ybnvcn3o50lrcpkh on task (destination_aet);
create index IDXe6odcfrgswxke8wtlj8bdehet on task (exporter_id);
create index IDXpknlk8ggf8lnq38lq3gacvvpt on task (check_missing);
create index IDX1lchdfbbwkjbg7a6coy5t8iq7 on task (check_different);
create index IDXow0nufrtniev7nkh7d0uv5mxe on task (compare_fields);
create index IDX6a0y0rsssms4mtm9bpkw8vgl6 on task (study_iuid, series_iuid, sop_iuid);
create index IDX1umoxe7ig9n21q885mncxeq9 on ups (updated_time);
create index IDXkgwfwmxj3i0n7c404uvhsav1g on ups (ups_priority);
create index IDXd3ejkrtcim0q3cbwpq4n9skes on ups (ups_label);
create index IDX7e44lxlg0m2l2wfdo3k2tec7o on ups (worklist_label);
create index IDXkh194du6g35fi5l80vbj18nnp on ups (start_date_time);
create index IDXe57ifctiig366oq9mhab8law3 on ups (expiration_date_time);
create index IDX1hdr3ml1rwugwkmo3eks4no5p on ups (expected_end_date_time);
create index IDXbrtgc3vpnoaq1xm80m568r16y on ups (input_readiness_state);
create index IDXsqoo5rr8pu2qe4gtdne3xh031 on ups (admission_id);
create index IDXcrl67piqoxiccp3i6ckktphdd on ups (replaced_iuid);
create index IDXc8obxmqpdcy37r3pjga2pukac on ups (ups_state);
alter table ups add constraint UKqck03rlxht9myv77sc79a480t unique (ups_iuid);
alter table ups add constraint UK3frtpy5cstsoxk5jxw9cutr33 unique (dicomattrs_fk);
create unique index UKhmvl80qis2pab8xtd9qyqea5b on ups (perf_name_fk) exclude null keys;
create index IDXrfium2ybikqm1f4xmi24mnv4u on ups_req (accession_no);
create index IDXemsk27nclko11ph2tcj5vk7hg on ups_req (req_service);
create index IDX524vr0q4c0kvyjwov74eru44x on ups_req (req_proc_id);
create index IDXhf0tly8umknn77civcsi0tdih on ups_req (study_iuid);
create unique index UKkocdb2pxx2fymu1modhneb4xm on ups_req (req_phys_name_fk) exclude null keys;
create index IDX5btv5autls384ulwues8lym4p on verify_observer (verify_datetime);
create unique index UK105wt9hglqsmtnoxgma9x18vj on verify_observer (observer_name_fk) exclude null keys;
alter table content_item add constraint FKfra6ee5jtybfp94ldpvva623o foreign key (code_fk) references code;
alter table content_item add constraint FK7rpy6unnb5b18b8ieuqr9w9i9 foreign key (name_fk) references code;
alter table content_item add constraint FKei15n1lk1h1e8f89e9ubalm7q foreign key (instance_fk) references instance;
alter table global_subscription add constraint FKqkchf2ue2j1p3fv2o94rhacvi foreign key (matchkeys_fk) references dicomattrs;
alter table hl7psu_task add constraint FKlvjl0o0sdhht440wccag722e4 foreign key (mpps_fk) references mpps;
alter table ian_task add constraint FKs3re94tlv0dbd5go33mwdk6dr foreign key (mpps_fk) references mpps;
alter table instance add constraint FKokrgkyvch35m6iwr309mawsna foreign key (dicomattrs_fk) references dicomattrs;
alter table instance add constraint FKqmk050ojwer9ujsbl74d4kf6e foreign key (srcode_fk) references code;
alter table instance add constraint FKe92yganh695k2y4hxfqxtesg1 foreign key (series_fk) references series;
alter table instance_req add constraint FK8leut3c0gfxcg6y3lbjlpt2bt foreign key (req_phys_name_fk) references person_name;
alter table instance_req add constraint FKmbg8j00cvubj76doba8funq8j foreign key (instance_fk) references instance;
alter table location add constraint FKpp9xjy946uspo3piayc6ofels foreign key (instance_fk) references instance;
alter table location add constraint FKendncyyv928u41dfo1x09s644 foreign key (uidmap_fk) references uidmap;
alter table mpps add constraint FKnhstgtx2w1xd61u4iqbn731pu foreign key (dicomattrs_fk) references dicomattrs;
alter table mpps add constraint FK2m3l9bh173bmq88ukf81yd9ow foreign key (discreason_code_fk) references code;
alter table mpps add constraint FKs7mb8qsr1mjijnw9bvx0dhqab foreign key (patient_fk) references patient;
alter table mwl_item add constraint FK9vjk41q61e9x9n4qfve6jud8s foreign key (dicomattrs_fk) references dicomattrs;
alter table mwl_item add constraint FKqui6rvqyg1qfujae4mw3o0nbm foreign key (inst_code_fk) references code;
alter table mwl_item add constraint FKpl6u7oyyp6o3ca8r9jbxv5oge foreign key (dept_code_fk) references code;
alter table mwl_item add constraint FKeursgxiy2u6ws39cgn1b25e6k foreign key (patient_fk) references patient;
alter table mwl_item add constraint FKf5l3x4bfqn3jv8mhj617kidx7 foreign key (perf_phys_name_fk) references person_name;
alter table patient add constraint FKe8j5c2rhq67rt3opqfggoplt2 foreign key (dicomattrs_fk) references dicomattrs;
alter table patient add constraint FKc66cfkdwiu7eq65kgmxilrocw foreign key (merge_fk) references patient;
alter table patient add constraint FKig1mwm5jpkyq6733t0hrf56g foreign key (pat_name_fk) references person_name;
alter table patient add constraint FKjh4miooi709vj5l6xi8h95f3e foreign key (resp_person_fk) references person_name;
alter table patient_id add constraint FKih8d22x50j7orytfmigrnssea foreign key (patient_fk) references patient;
alter table rejected_instance add constraint FK27xpx6okj63sunyo76tf0s913 foreign key (reject_code_fk) references code;
alter table rel_study_pcode add constraint FKlia0q74j0xhj2u0avy1wbbx7q foreign key (pcode_fk) references code;
alter table rel_study_pcode add constraint FKta5515ogakmavm1swi6tw1n2n foreign key (study_fk) references study;
alter table rel_task_dicomattrs add constraint FK53sqs6uiua7ff45u94lrv50t7 foreign key (dicomattrs_fk) references dicomattrs;
alter table rel_task_dicomattrs add constraint FKhd3dj3u43xxgdxn7w5imp5ank foreign key (task_fk) references task;
alter table rel_ups_perf_code add constraint FK6om2durembdfk2rmd29dlcm2t foreign key (perf_code_fk) references code;
alter table rel_ups_perf_code add constraint FK31cp9ux8xb2dcu0nv84tgd8um foreign key (ups_fk) references ups;
alter table rel_ups_station_class_code add constraint FK62f8dua3p8bxv3vilctc7pd42 foreign key (station_class_code_fk) references code;
alter table rel_ups_station_class_code add constraint FKf0y92ufejerf9dwv8sl6kdkae foreign key (ups_fk) references ups;
alter table rel_ups_station_location_code add constraint FKp5d05caciw2cqik5uqt7r8w6p foreign key (station_location_code_fk) references code;
alter table rel_ups_station_location_code add constraint FKlxb9i1b48mfxo0y72fpeoxl48 foreign key (ups_fk) references ups;
alter table rel_ups_station_name_code add constraint FK308ga4grefjjwmrwtsirjmbhh foreign key (station_name_code_fk) references code;
alter table rel_ups_station_name_code add constraint FKd4gjwmhmlphp865fxiqjypwo2 foreign key (ups_fk) references ups;
alter table series add constraint FKtih7mdqwcklym58jd9wn3itjq foreign key (dicomattrs_fk) references dicomattrs;
alter table series add constraint FK6nn55bx7pqlmimgfnhfpjra94 foreign key (inst_code_fk) references code;
alter table series add constraint FKnpdeeclrflvuxajeqjmk6utf foreign key (dept_code_fk) references code;
alter table series add constraint FK84fpblugeadm5u6dw1idi80a0 foreign key (metadata_fk) references metadata;
alter table series add constraint FK5p2gub2u40fyh2ch6bp6nan4l foreign key (perf_phys_name_fk) references person_name;
alter table series add constraint FK3196nws7ngapmsmtoafb4e88j foreign key (study_fk) references study;
alter table series_query_attrs add constraint FKsk791fnscydy7whj3lvrp3j1a foreign key (series_fk) references series;
alter table series_req add constraint FK15lid2bqbrsmjatxiq0vug4wq foreign key (req_phys_name_fk) references person_name;
alter table series_req add constraint FK1gs2u7w7uefvi7dcjg9vlp23x foreign key (series_fk) references series;
alter table soundex_code add constraint FKctukwjaer0axw2jwv3bfj67r2 foreign key (person_name_fk) references person_name;
alter table sps_station_aet add constraint FKmeklscb7t55i8mb2d4cjucywd foreign key (mwl_item_fk) references mwl_item;
alter table study add constraint FK2ktu3hbxxalrngfhyp4j6pf70 foreign key (dicomattrs_fk) references dicomattrs;
alter table study add constraint FKocnq3so9ej7h0mnd6a6arjf6c foreign key (patient_fk) references patient;
alter table study add constraint FKndwmntu3lsjd78erctnbposyr foreign key (ref_phys_name_fk) references person_name;
alter table study_query_attrs add constraint FKmu32jq25y6qkimi7hn7d4s2hp foreign key (study_fk) references study;
alter table subscription add constraint FKh9mdhriyx32gr91pfdgnl48f0 foreign key (ups_fk) references ups;
alter table ups add constraint FKaddby69vtwxpssgaa12ct6pn3 foreign key (dicomattrs_fk) references dicomattrs;
alter table ups add constraint FKhy3cd5se2avt08upapu19y1g6 foreign key (perf_name_fk) references person_name;
alter table ups add constraint FKjcmu4x6x02r0tc1d28xb3nf17 foreign key (patient_fk) references patient;
alter table ups add constraint FKr3t1gbo2e42oleaqeastvv5ej foreign key (ups_code_fk) references code;
alter table ups_req add constraint FK1b5veu90oftv8o95nxx2xndpb foreign key (req_phys_name_fk) references person_name;
alter table ups_req add constraint FKh1sjrdq663dnk8j7hsthun4ie foreign key (ups_fk) references ups;
alter table verify_observer add constraint FK8vo3f94xgum0qajsjqnlnqjo4 foreign key (observer_name_fk) references person_name;
alter table verify_observer add constraint FKkpcwkrsipamdg81mao7nkmdc2 foreign key (instance_fk) references instance;
