--applies only to archive versions direct-installed / updated to 5.34.0 to 5.34.3 versions
alter table content_item alter column text_value nvarchar(255) null;
alter table hl7psu_task alter column accession_no nvarchar(255) null;