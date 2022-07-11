-- can be applied on archive running archive 5.25
alter table hl7psu_task
    add accession_no varchar2(255);

-- part 2: shall be applied on stopped archive before starting 5.26

-- part 3: can be applied on already running archive 5.26