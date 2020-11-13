#!/usr/bin/awk -f
BEGIN { FS = "[ )]" }
{
    if ($6 == "UK_t1p7jajas0mu12sx8jvtp2y0f") {
            print "create unique index UK_t1p7jajas0mu12sx8jvtp2y0f on issuer(entity_uid, entity_uid_type) where entity_id is not null and entity_uid_type is not null"
    } else if ($6 == "UK_31gvi9falc03xs94m8l3pgoid") {
            print "create unique index UK_31gvi9falc03xs94m8l3pgoid on patient_id(pat_id, issuer_fk) where pat_id is not null"
    } else {
        print $0
    }
}
