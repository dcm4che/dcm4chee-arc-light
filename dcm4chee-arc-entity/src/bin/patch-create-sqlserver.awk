#!/usr/bin/awk -f
BEGIN { FS = "[ )]" }
{
    if ($1 == "create") {
        if ($3 == "dicomattrs") {
            print "create table dicomattrs (pk bigint identity not null, attrs varbinary(MAX) not null, primary key (pk));"
        } else if ($3 == "task") {
            print "create table task (pk bigint identity not null, batch_id varchar(255), check_different bit, check_missing bit, compare_fields varchar(255), completed int, created_time datetime2(6) not null, destination_aet varchar(255), device_name varchar(255) not null, different int not null, error_comment varchar(255), error_msg varchar(255), exporter_id varchar(255), failed int, local_aet varchar(255), matches int not null, missing int not null, modalities varchar(255), num_failures int not null, num_instances int, outcome_msg varchar(255), payload varbinary(MAX), proc_end_time datetime2(6), proc_start_time datetime2(6), query_str varchar(255), queue_name varchar(255) not null, remaining int, remote_aet varchar(255), rq_uri varchar(4000), rq_host varchar(255), rq_user_id varchar(255), scheduled_time datetime2(6) not null, series_iuid varchar(255), sop_iuid varchar(255), task_status smallint not null check (task_status between 0 and 6), status_code int, storage_ids varchar(255), stgcmt_policy smallint check (stgcmt_policy between 0 and 5), study_iuid varchar(255), task_type smallint not null check (task_type between 0 and 10), update_location_status bit, updated_time datetime2(6) not null, version bigint, warning int not null, primary key (pk));"
        } else if ($3 == "uidmap") {
            print "create table uidmap (pk bigint identity not null, uidmap varbinary(MAX) not null, primary key (pk));"
        } else {
            print $0
        }
    } else {
        print $0
    }
}
