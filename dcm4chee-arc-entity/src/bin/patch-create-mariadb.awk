#!/usr/bin/awk -f
{
    # Hibernate 7 MariaDBDialect generates alter table if exists: keep alter table as in existing scripts
    sub(/^alter table if exists /, "alter table ")
    print $0
}
