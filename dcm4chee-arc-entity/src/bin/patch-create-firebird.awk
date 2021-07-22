#!/usr/bin/awk -f
BEGIN { FS = "[ )]" }
{
    if ($1 == "create") {
        if ($3 == "code") {
                print "create table code (pk numeric(18,0) not null, code_meaning varchar(255) not null, code_value varchar(16) not null, code_designator varchar(16) not null, code_version varchar(16) not null, primary key (pk));"
        } else if ($3 == "task") {
                print "create table task (pk numeric(18,0) not null, batch_id varchar(64), check_different smallint, check_missing smallint, compare_fields varchar(64), completed integer, created_time timestamp not null, destination_aet varchar(64), device_name varchar(64) not null, different integer not null, error_comment varchar(255), error_msg varchar(255), exporter_id varchar(64), failed integer, local_aet varchar(64), matches integer not null, missing integer not null, modalities varchar(255), num_failures integer not null, num_instances integer, outcome_msg varchar(255), payload blob not null, proc_end_time timestamp, proc_start_time timestamp, query_str varchar(255), queue_name varchar(64) not null, remaining integer, remote_aet varchar(64), rq_uri varchar(4000), rq_host varchar(255), rq_user_id varchar(255), scheduled_time timestamp not null, series_iuid varchar(64), sop_iuid varchar(64), task_status integer not null, status_code integer, storage_ids varchar(64), stgcmt_policy integer, study_iuid varchar(64), task_type integer not null, update_location_status smallint, updated_time timestamp not null, version numeric(18,0), warning integer not null, primary key (pk));"
        } else if ($3 == "issuer") {
                print "create table issuer (pk numeric(18,0) not null, entity_id varchar(64), entity_uid varchar(64), entity_uid_type varchar(16), primary key (pk));"
        } else if ($3 == "mwl_item") {
                print "create table mwl_item (pk numeric(18,0) not null, accession_no varchar(16) not null, admission_id varchar(64) not null, created_time timestamp not null, institution varchar(64) not null, department varchar(64) not null, modality varchar(16) not null, req_proc_id varchar(16) not null, sps_id varchar(16) not null, sps_start_date varchar(16) not null, sps_start_time varchar(16) not null, sps_status integer not null, study_iuid varchar(64) not null, updated_time timestamp not null, version numeric(18,0), dicomattrs_fk numeric(18,0) not null, inst_code_fk numeric(18,0), dept_code_fk numeric(18,0), accno_issuer_fk numeric(18,0), admid_issuer_fk numeric(18,0), patient_fk numeric(18,0) not null, perf_phys_name_fk numeric(18,0), primary key (pk));"
        } else {
            print $0
        }
    } else {
        print $0
    }
}
