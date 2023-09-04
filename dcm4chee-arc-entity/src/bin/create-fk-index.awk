#!/usr/bin/awk -f
$9 == "foreign" && $11 != "(dicomattrs_fk)" { print "create index", $8, "on", $5, $11, ";" }
