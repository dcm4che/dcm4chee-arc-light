-- part 1: can be applied on archive running archive 5.32
alter table ups add perf_name_fk bigint unique;
alter table if exists ups add constraint FKhy3cd5se2avt08upapu19y1g6 foreign key (perf_name_fk) references person_name;
create index FKhy3cd5se2avt08upapu19y1g6 on ups (perf_name_fk) ;

-- part 2: shall be applied on stopped archive before starting 5.33

-- part 3: can be applied on already running archive 5.33
