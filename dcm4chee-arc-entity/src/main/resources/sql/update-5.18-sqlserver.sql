-- part 1: can be applied on archive running archive 5.17
create table rejected_instance (
   pk bigint identity not null,
   created_time datetime2 not null,
   series_iuid varchar(255) not null,
   sop_cuid varchar(255) not null,
   sop_iuid varchar(255) not null,
   study_iuid varchar(255) not null,
   reject_code_fk bigint,
   primary key (pk));

alter table rejected_instance add constraint UK_6liqevdmi0spifxf2vrh18wkp  unique (study_iuid, series_iuid, sop_iuid);
alter table rejected_instance add constraint FK_iafiq2ugv5rd6fonwd0vd7xdx foreign key (reject_code_fk) references code;

create sequence rejected_instance_pk_seq as int start with 1 increment by 1 go;
set identity_insert rejected_instance on;

insert into rejected_instance (pk, created_time, sop_cuid, sop_iuid, reject_code_fk, series_iuid, study_iuid)
    (select next value for rejected_instance_pk_seq, i.updated_time, i.sop_cuid, i.sop_iuid, i.reject_code_fk, se.series_iuid, st.study_iuid
     from instance i join series se on i.series_fk = se.pk join study st on se.study_fk = st.pk
     where i.reject_code_fk is not null);

create index UK_owm55at56tdjitsncsrhr93xj on rejected_instance (created_time);
create index FK_iafiq2ugv5rd6fonwd0vd7xdx on rejected_instance (reject_code_fk) ;

-- part 2: shall be applied on stopped archive before starting 5.18
insert into rejected_instance (pk, created_time, sop_cuid, sop_iuid, reject_code_fk, series_iuid, study_iuid)
    (select next value for rejected_instance_pk_seq, i.updated_time, i.sop_cuid, i.sop_iuid, i.reject_code_fk, se.series_iuid, st.study_iuid
     from instance i join series se on i.series_fk = se.pk join study st on se.study_fk = st.pk
     where i.reject_code_fk is not null and i.updated_time > getdate() and not exists (
         select 1 from rejected_instance ri
         where ri.study_iuid = st.study_iuid and ri.series_iuid = se.series_iuid and ri.sop_iuid = i.sop_iuid));

-- part 3: can be applied on already running archive 5.18
alter table instance drop constraint FK_6pnwsvi69g5ypye6gjo26vn7e;
alter table instance drop column reject_code_fk;
