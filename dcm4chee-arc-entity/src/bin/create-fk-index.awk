#!/usr/bin/awk -f
$7 == "foreign" { print "create index", $6, "on", $3, $9, ";" }
