#!/usr/bin/awk -f
BEGIN { FS = "[ )]" }
{
    if ($3 == "id_sequence") {
            print "create table id_sequence (name varchar(64) not null, next_value integer not null, version bigint, primary key (name));"
    } else if ($3 == "UK_cxaqwh62doxvy1itpdi43c681") {
            print "create index UK_cxaqwh62doxvy1itpdi43c681 on export_task (device_name(64), scheduled_time);"
    } else if ($3 == "UK_r3oh859i9osv3aluoc8dcx9wk") {
            print "create index UK_r3oh859i9osv3aluoc8dcx9wk on location (storage_id(64), status);"
    } else if ($2 == "index" \
            && $6 != "(inst_no" \
            && $6 != "(updated_time" \
            && $6 != "(multi_ref" \
            && $6 != "(sps_status" \
            && $6 != "(num_studies" \
            && $6 != "(msg_status" \
            && $6 != "(rejection_state" \
            && $6 != "(series_no" \
            && $6 != "(failed_retrieves" \
            && $6 != "(sx_pn_comp" \
            && $6 != "(sx_pn_comp_part" \
            && $6 != "(stgcmt_status" \
            && $6 != "(access_time" \
            && $6 != "(created_time" \
        ) {
        print $1 " " $2 " " $3 " " $4 " " $5 " " $6 "(64));"
    } else {
        print $0
    }
}
