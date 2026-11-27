#!/usr/bin/awk -f
{
    # Hibernate 7 OracleDialect generates timestamp(9) by default; keep timestamp(6) as in existing DBs
    gsub(/ timestamp\(9\)/, " timestamp(6)")
    print $0
}
