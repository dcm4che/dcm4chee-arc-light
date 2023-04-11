-- can be applied on archive running archive 5.29
alter table mwl_item
    add worklist_label varchar(64);

update mwl_item set worklist_label=local_aet;

create index UK_88bqeff7thxsmgcmtrg5l3td on mwl_item (worklist_label);

-- part 2: shall be applied on stopped archive before starting 5.30
update mwl_item set worklist_label=local_aet where worklist_label is null;
alter table mwl_item alter local_aet drop not null;

-- part 3: can be applied on already running archive 5.30
alter table mwl_item alter column worklist_label set not null;

drop index UK_9ockpkbetj7a97for0s1jhasi;
alter table mwl_item drop local_aet;
