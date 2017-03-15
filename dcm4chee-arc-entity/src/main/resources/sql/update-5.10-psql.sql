update study set ext_retrieve_aet = '*' where ext_retrieve_aet is null;
alter table study alter ext_retrieve_aet set not null;
