#!/usr/bin/awk -f
$7 == "foreign" && $9 != "(dicomattrs_fk)" { print "create index", $6, "on", $3, $9, ";" }
