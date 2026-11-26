-- applies only to databases created with create-oracle.sql of archive 5.34.0 or later
-- (content_item.text_value and hl7psu_task.accession_no were created NOT NULL);
-- databases updated from older versions already allow null: the script then does nothing
begin
  for c in (select table_name, column_name from user_tab_columns
             where nullable = 'N'
               and (table_name, column_name) in (('CONTENT_ITEM', 'TEXT_VALUE'), ('HL7PSU_TASK', 'ACCESSION_NO'))) loop
    execute immediate 'alter table ' || c.table_name || ' modify (' || c.column_name || ' null)';
  end loop;
end;
/
