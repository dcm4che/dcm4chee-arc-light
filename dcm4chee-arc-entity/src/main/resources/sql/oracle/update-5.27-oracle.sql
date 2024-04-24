-- part 1: can be applied on archive running archive 5.25
alter table hl7psu_task
    add accession_no varchar2(255);
create table instance_req (
    pk number(19,0) not null,
    accession_no varchar2(255 char) not null,
    accno_entity_id varchar2(255 char),
    accno_entity_uid varchar2(255 char),
    accno_entity_uid_type varchar2(255 char),
    req_proc_id varchar2(255 char) not null,
    req_service nvarchar2(255) not null,
    sps_id varchar2(255 char) not null,
    study_iuid varchar2(255 char) not null,
    req_phys_name_fk number(19,0),
    instance_fk number(19,0),
    primary key (pk));

alter table instance_req
    add constraint FK_cqmmps9maltjybl44t4cck404 foreign key (req_phys_name_fk) references person_name;
alter table instance_req
    add constraint FK_47n586hkafgp9m1etqohgfybl foreign key (instance_fk) references instance;
alter table series
    add modified_time timestamp;
update series set modified_time = updated_time;

create index UK_cqpv94ky100d0eguhrxpyplmv on instance_req (accession_no);
create index UK_n32ktg5h9xc1ex9x8g69w1s10 on instance_req (req_service);
create index UK_7pudwdgrg9wwc73wo65hpg517 on instance_req (req_proc_id);
create index UK_43h9ogidkcnex0e14q6u0c3jn on instance_req (sps_id);
create index UK_1typgaxhn4d0pt1f0vlp18wvb on instance_req (study_iuid);

create index FK_cqmmps9maltjybl44t4cck404 on instance_req (req_phys_name_fk) ;
create index FK_47n586hkafgp9m1etqohgfybl on instance_req (instance_fk) ;

create sequence instance_req_pk_seq;
-- part 2: shall be applied on stopped archive before starting 5.26
update series set modified_time = updated_time where modified_time is null;

-- part 3: can be applied on already running archive 5.26
alter table series modify modified_time not null;