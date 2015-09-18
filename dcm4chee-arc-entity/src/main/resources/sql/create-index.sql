create unique index code_idx on code (code_value, code_designator, code_version);

create index content_item_rel_type_idx on content_item (rel_type);
create index content_item_text_value_idx on content_item (text_value);

create unique index issuer_entity_id_idx on id_issuer (entity_id);
create unique index issuer_entity_uid_idx on id_issuer (entity_uid, entity_uid_type);

create index family_name_idx on person_name (family_name);
create index given_name_idx on person_name (given_name);
create index middle_name_idx on person_name (middle_name);
create index i_family_name_idx on person_name (i_family_name);
create index i_given_name_idx on person_name (i_given_name);
create index i_middle_name_idx on person_name (i_middle_name);
create index p_family_name_idx on person_name (p_family_name);
create index p_given_name_idx on person_name (p_given_name);
create index p_middle_name_idx on person_name (p_middle_name);

create index sx_code_value_idx on soundex_code (sx_code_value);
create index sx_pn_comp_idx on soundex_code (sx_pn_comp);
create index sx_pn_comp_part_idx on soundex_code (sx_pn_comp_part);

create unique index inst_sop_iuid_idx on instance (sop_iuid);
create index inst_sop_cuid_idx on instance (sop_cuid);
create index inst_no_idx on instance (inst_no);
create index inst_content_date_idx on instance (content_date);
create index inst_content_time_idx on instance (content_time);
create index inst_sr_verified_idx on instance (sr_verified);
create index inst_sr_complete_idx on instance (sr_complete);
create index inst_availability on instance (availability);
create index inst_custom1_idx on instance (inst_custom1);
create index inst_custom2_idx on instance (inst_custom2);
create index inst_custom3_idx on instance (inst_custom3);

create index pat_id_idx on patient_id (pat_id, issuer_fk);

create index pat_birthdate_idx on patient (pat_birthdate);
create index pat_sex_idx on patient (pat_sex);
create index pat_custom1_idx on patient (pat_custom1);
create index pat_custom2_idx on patient (pat_custom2);
create index pat_custom3_idx on patient (pat_custom3);

create unique index series_iuid_idx on series (series_iuid);
create index series_no_idx on series (series_no);
create index series_modality_idx on series (modality);
create index series_station_name_idx on series (station_name);
create index series_pps_start_date_idx on series (pps_start_date);
create index series_pps_start_time_idx on series (pps_start_time);
create index series_body_part_idx on series (body_part);
create index series_laterality_idx on series (laterality);
create index series_desc_idx on series (series_desc);
create index series_institution_idx on series (institution);
create index series_department_idx on series (department);
create index series_custom1_idx on series (series_custom1);
create index series_custom2_idx on series (series_custom2);
create index series_custom3_idx on series (series_custom3);

create index series_req_accession_no_idx on series_req (accession_no);
create index series_req_service_idx on series_req (req_service);
create index series_req_proc_id_idx on series_req (req_proc_id);
create index series_req_sps_id_idx on series_req (sps_id);
create index series_req_study_iuid_idx on series_req (study_iuid);

create unique index study_iuid_idx on study (study_iuid);
create index study_id_idx on study (study_id);
create index study_date_idx on study (study_date);
create index study_time_idx on study (study_time);
create index study_accession_no_idx on study (accession_no);
create index study_desc_idx on study (study_desc);
create index study_custom1_idx on study (study_custom1);
create index study_custom2_idx on study (study_custom2);
create index study_custom3_idx on study (study_custom3);
create index study_access_control_id_idx on study (access_control_id);

create index vo_verify_datetime_idx on verify_observer (verify_datetime);

create unique index mpps_iuid_idx on mpps (mpps_iuid);

create index sps_station_aet_idx on sps_station_aet (station_aet);

create index study_view_id_idx on study_query_attrs(view_id);
create index series_view_id_idx on series_query_attrs(view_id);
