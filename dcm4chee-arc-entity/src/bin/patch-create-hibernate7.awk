#!/usr/bin/awk -f
# Common post-processing of the create-<db>.sql scripts generated with Hibernate 7, for all DBs.
# Run on target/generated-schema/create-<db>.sql before src/bin/patch-create-<db>.awk (if any), e.g.
#   awk -f src/bin/patch-create-hibernate7.awk target/generated-schema/create-psql.sql > src/main/resources/sql/psql/create-psql.sql
#   awk -f src/bin/patch-create-hibernate7.awk target/generated-schema/create-mysql.sql | awk -f src/bin/patch-create-mysql.awk > src/main/resources/sql/mysql/create-mysql.sql
#
# Hibernate 7.4 (HHH-20130) adds a unique constraint on (owner, value) for @ElementCollection sets.
# Existing DBs have none, and on case-insensitive DBs (SQL Server, MySQL, MariaDB) it would reject values
# a Java Set accepts (e.g. CT1 / ct1): keep the tables without it.
#   - inline in create table (h2, oracle, psql)
#   - alter table ... add constraint UK... unique (firebird, mariadb, mysql)
#   - create unique [nonclustered] index UK... (db2, sqlserver)
/^alter table (if exists )?(study_access_control_id|series_access_control_id|sps_station_aet) add constraint UK[a-z0-9]+ unique / { next }
/^create unique (nonclustered )?index UK[a-z0-9]+ on (study_access_control_id|series_access_control_id|sps_station_aet) / { next }
{
    gsub(/, unique \(study_fk, access_control_id\)/, "")
    gsub(/, unique \(series_fk, access_control_id\)/, "")
    gsub(/, unique \(mwl_item_fk, station_aet\)/, "")
    print
}
