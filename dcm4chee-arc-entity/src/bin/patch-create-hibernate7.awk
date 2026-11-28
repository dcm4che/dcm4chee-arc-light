#!/usr/bin/awk -f
# Common post-processing of the create-<db>.sql scripts generated with Hibernate 7, for all DBs.
# Run on target/generated-schema/create-<db>.sql before src/bin/patch-create-<db>.awk (if any), e.g.
#   awk -f src/bin/patch-create-hibernate7.awk target/generated-schema/create-psql.sql > src/main/resources/sql/psql/create-psql.sql
#   awk -f src/bin/patch-create-hibernate7.awk target/generated-schema/create-mysql.sql | awk -f src/bin/patch-create-mysql.awk > src/main/resources/sql/mysql/create-mysql.sql
# (patch-create-<db>.awk exists for db2, firebird, mariadb, mysql, oracle, sqlserver)
#
# Hibernate 7.4 (HHH-20130) adds a unique constraint on (owner, value) for @ElementCollection sets.
# Existing DBs have none, and on case-insensitive DBs (SQL Server, MySQL, MariaDB) it would reject values
# a Java Set accepts (e.g. CT1 / ct1): keep the tables without it.
#   - inline in create table (h2, oracle, psql)
#   - alter table ... add constraint UK... unique (firebird, mariadb, mysql)
#   - create unique [nonclustered] index UK... (db2, sqlserver)
#
# Hibernate 7.3 (HHH-20076) generates the @Version columns as not null. Existing DBs have them nullable
# (Hibernate always sets the value, so no functional difference): keep them nullable.
#
# Hibernate 7 encloses the condition of check constraints in an additional pair of parentheses,
# e.g. check ((pps_status between 0 and 2)), check ((study_deleting in (0,1))): keep check (...).
function unwrap_checks(s,    out, i, j, n, c, depth) {
    out = ""
    while ((i = index(s, "check ((")) > 0) {
        out = out substr(s, 1, i + 6)       # ... "check ("
        s = substr(s, i + 7)                # "(" <condition> ")" ")" ...
        n = length(s); depth = 0
        for (j = 1; j <= n; j++) {
            c = substr(s, j, 1)
            if (c == "(") depth++
            else if (c == ")" && --depth == 0) break
        }
        out = out substr(s, 2, j - 2)       # <condition> without the additional parentheses
        s = substr(s, j + 1)
    }
    return out s
}
/^alter table (if exists )?(study_access_control_id|series_access_control_id|sps_station_aet) add constraint UK[a-z0-9]+ unique / { next }
/^create unique (nonclustered )?index UK[a-z0-9]+ on (study_access_control_id|series_access_control_id|sps_station_aet) / { next }
{
    gsub(/, unique \(study_fk, access_control_id\)/, "")
    gsub(/, unique \(series_fk, access_control_id\)/, "")
    gsub(/, unique \(mwl_item_fk, station_aet\)/, "")
    gsub(/, version bigint not null/, ", version bigint")
    gsub(/, version number\(19,0\) not null/, ", version number(19,0)")
    print unwrap_checks($0)
}
