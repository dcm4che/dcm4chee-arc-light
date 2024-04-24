-- part 1: can be applied on archive running archive 5.29
alter table mwl_item
    add worklist_label varchar(255);

update mwl_item set worklist_label=local_aet;

-- part 2: shall be applied on stopped archive before starting 5.30
update mwl_item set worklist_label=local_aet where worklist_label is null;
alter table mwl_item alter column local_aet varchar(255) null;

-- part 3: can be applied on already running archive 5.30
alter table mwl_item alter column worklist_label varchar(255) not null;
create index UK_88bqeff7thxsmgcmtrg5l3td on mwl_item (worklist_label);

drop index UK_9ockpkbetj7a97for0s1jhasi on mwl_item;
alter table mwl_item drop column local_aet;
