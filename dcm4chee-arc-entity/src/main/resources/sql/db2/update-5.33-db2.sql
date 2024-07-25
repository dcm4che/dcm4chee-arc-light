-- part 1: can be applied on archive running archive 5.32
alter table series add access_control_id varchar(255);
update series set access_control_id = '*';
create index IDXdhohkk791t9yvrlt0lihik992 on patient (created_time);
create index IDXr9qbr5jv4ejclglvyvtsynuo9 on series (access_control_id);

-- part 2: shall be applied on stopped archive before starting 5.33
update series set access_control_id = '*' where access_control_id is null;

-- part 3: can be applied on already running archive 5.33
alter table series alter column access_control_id set not null;
