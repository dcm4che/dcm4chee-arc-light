#!/usr/bin/awk -f
BEGIN { FS = "[ )]" }
{
    if ($6 == "UKq9cv3b9n0uv93ugud52uiw9k1") {
        print "alter table patient_id add constraint UKq9cv3b9n0uv93ugud52uiw9k1 unique (pat_id(128), entity_id(128), entity_uid(128), entity_uid_type(128), pat_name(128));"
    } else {
        print $0
    }
}
