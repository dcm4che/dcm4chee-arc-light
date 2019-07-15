-- part 1: can be applied on archive running archive 5.17
create table rejected_instance (
    pk int8 not null,
    created_time timestamp not null,
    series_iuid varchar(255) not null,
    sop_cuid varchar(255) not null,
    sop_iuid varchar(255) not null,
    study_iuid varchar(255) not null,
    reject_code_fk int8,
    primary key (pk));

alter table rejected_instance add constraint UK_6liqevdmi0spifxf2vrh18wkp  unique (study_iuid, series_iuid, sop_iuid);
create index UK_owm55at56tdjitsncsrhr93xj on rejected_instance (created_time);
alter table rejected_instance add constraint FK_iafiq2ugv5rd6fonwd0vd7xdx foreign key (reject_code_fk) references code;
create index FK_iafiq2ugv5rd6fonwd0vd7xdx on rejected_instance (reject_code_fk) ;
create sequence rejected_instance_pk_seq;

-- part 2: shall be applied on stopped archive before starting 5.18

-- part 3: can be applied on already running archive 5.18
