-- part 1: can be applied on archive running archive 5.21
alter table mwl_item
    add admission_id varchar(255);
alter table mwl_item
    add institution varchar(255);
alter table mwl_item
    add department varchar(255);
alter table mwl_item
    add inst_code_fk bigint;
alter table mwl_item
    add dept_code_fk bigint;
alter table mwl_item
    add admid_issuer_fk bigint;

alter table series
    add dept_code_fk bigint;

alter table study
    add admission_id varchar(255);
alter table study
    add admid_issuer_fk bigint;

alter table study_query_attrs alter column cuids_in_study varchar(4000);

alter table hl7psu_task add series_iuid varchar(255);

update mwl_item set admission_id = '*', institution = '*', department = '*';
update study set admission_id = '*';

create index UK_tlkw80b7pbutfj19vh6et2vs7 on mwl_item (admission_id);
create index UK_8qkftk7n30hla3v1frep9vg2q on mwl_item (institution);
create index UK_ksy3uy0rvpis1sqqeojlet7lb on mwl_item (department);

create index UK_n5froudmhk14pbhgors43xi68 on study (admission_id);

alter table mwl_item add constraint FK_t4vpsywvy0axeutmdgc0ye3nk foreign key (inst_code_fk) references code;
alter table mwl_item add constraint FK_hqecoo67sflk190dxyc0hnf0c foreign key (dept_code_fk) references code;
alter table mwl_item add constraint FK_9k8x73a91nd9q7ux7h5itkyh5 foreign key (admid_issuer_fk) references issuer;

alter table series add constraint FK_avp2oeuufo8axv5j184cchrop foreign key (dept_code_fk) references code;

alter table study add constraint FK_9fqno60wc3gr4376ov1xlfme4 foreign key (admid_issuer_fk) references issuer;

create index FK_t4vpsywvy0axeutmdgc0ye3nk on mwl_item (inst_code_fk) ;
create index FK_hqecoo67sflk190dxyc0hnf0c on mwl_item (dept_code_fk) ;
create index FK_9k8x73a91nd9q7ux7h5itkyh5 on mwl_item (admid_issuer_fk) ;

create index FK_avp2oeuufo8axv5j184cchrop on series (dept_code_fk) ;

create index FK_9fqno60wc3gr4376ov1xlfme4 on study (admid_issuer_fk) ;

-- part 2: shall be applied on stopped archive before starting 5.22
update mwl_item set admission_id = '*', institution = '*', department = '*' where admission_id is null;
update study set admission_id = '*' where admission_id is null;

-- part 3: can be applied on already running archive 5.22
alter table mwl_item
    alter column admission_id set not null;
alter table mwl_item
    alter column institution set not null;
alter table mwl_item
    alter column department set not null;

alter table study
    alter column admission_id set not null;