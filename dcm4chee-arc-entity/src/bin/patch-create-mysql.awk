#!/usr/bin/awk -f
BEGIN { FS = "[ )]" }
{
    if ($3 == "id_sequence") {
            print "create table id_sequence (name varchar(64) not null, next_value integer not null, version bigint, primary key (name));"
    } else if ($3 == "UK_hb9rftf7opmg56nkg7dkvsdc8") {
            print "create index UK_hb9rftf7opmg56nkg7dkvsdc8 on export_task (study_iuid(64), series_iuid(64), sop_iuid(64));"
    } else if ($3 == "UK_iudr0qmrm15i2evq1733h1ace") {
            print "create index UK_iudr0qmrm15i2evq1733h1ace on stgver_task (study_iuid(64), series_iuid(64), sop_iuid(64));"
    } else if ($3 == "UK_r3oh859i9osv3aluoc8dcx9wk") {
            print "create index UK_r3oh859i9osv3aluoc8dcx9wk on location (storage_id(64), status);"
    } else if ($3 == "UK_f7c9hmq8pfypohkgkp5vkbhxp") {
            print "create index UK_f7c9hmq8pfypohkgkp5vkbhxp on metadata (storage_id(64), status);"
    } else if ($2 == "index" \
            && $6 != "(created_time" \
            && $6 != "(updated_time" \
            && $6 != "(scheduled_time" \
            && $6 != "(inst_no" \
            && $6 != "(multi_ref" \
            && $6 != "(sps_status" \
            && $6 != "(num_studies" \
            && $6 != "(msg_status" \
            && $6 != "(completeness" \
            && $6 != "(rejection_state" \
            && $6 != "(expiration_state" \
            && $6 != "(study_size" \
            && $6 != "(series_no" \
            && $6 != "(failed_retrieves" \
            && $6 != "(stgver_time" \
            && $6 != "(stgver_failures" \
            && $6 != "(compress_time" \
            && $6 != "(compress_failures" \
            && $6 != "(sx_pn_comp" \
            && $6 != "(sx_pn_comp_part" \
            && $6 != "(stgcmt_status" \
            && $6 != "(access_time" \
            && $6 != "(metadata_update_time" \
            && $6 != "(metadata_update_failures" \
            && $6 != "(inst_purge_time" \
            && $6 != "(inst_purge_state" \
            && $6 != "(check_missing" \
            && $6 != "(check_different" \
            && $6 != "(verification_status" \
            && $6 != "(verification_time" \
            && $6 != "(ups_priority" \
            && $6 != "(input_readiness_state" \
            && $6 != "(ups_state" \
        ) {
        print $1 " " $2 " " $3 " " $4 " " $5 " " $6 "(64));"
    } else {
        print $0
    }
}
