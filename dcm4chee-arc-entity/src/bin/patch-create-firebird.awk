#!/usr/bin/awk -f
BEGIN { FS = "[ )]" }
{
    if ($1 == "create") {
        if ($3 == "code") {
                print "create table code (pk numeric(18,0) not null, code_meaning varchar(255) not null, code_value varchar(16) not null, code_designator varchar(16) not null, code_version varchar(16) not null, primary key (pk));"
        } else if ($3 == "export_task") {
                print "create table export_task (pk numeric(18,0) not null, created_time timestamp not null, device_name varchar(255) not null, exporter_id varchar(255) not null, modalities varchar(255), num_instances integer, scheduled_time timestamp not null, series_iuid varchar(64) not null, sop_iuid varchar(64) not null, study_iuid varchar(64) not null, updated_time timestamp not null, version numeric(18,0), queue_msg_fk numeric(18,0), primary key (pk));"
        } else if ($3 == "stgver_task") {
                print "create table stgver_task (pk numeric(18,0) not null, completed integer not null, created_time timestamp not null, failed integer not null, local_aet varchar(255) not null, series_iuid varchar(64), sop_iuid varchar(64), storage_ids varchar(255), stgcmt_policy integer, study_iuid varchar(64) not null, update_location_status smallint, updated_time timestamp not null, queue_msg_fk numeric(18,0) not null, primary key (pk));"
        } else if ($3 == "issuer") {
                print "create table issuer (pk numeric(18,0) not null, entity_id varchar(255), entity_uid varchar(64), entity_uid_type varchar(16), primary key (pk));"
        } else if ($3 == "mwl_item") {
                print "create table mwl_item (pk numeric(18,0) not null, accession_no varchar(255) not null, created_time timestamp not null, modality varchar(255) not null, req_proc_id varchar(255) not null, sps_id varchar(16) not null, sps_start_date varchar(255) not null, sps_start_time varchar(255) not null, sps_status integer not null, study_iuid varchar(64) not null, updated_time timestamp not null, version numeric(18,0), dicomattrs_fk numeric(18,0) not null, accno_issuer_fk numeric(18,0), patient_fk numeric(18,0) not null, perf_phys_name_fk numeric(18,0), primary key (pk));"
        } else {
            print $0
        }
    } else {
        print $0
    }
}
