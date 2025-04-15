-- part 1: can be applied on archive running archive 5.33
alter table ups add perf_name_fk bigint unique;
alter table if exists ups add constraint FKhy3cd5se2avt08upapu19y1g6 foreign key (perf_name_fk) references person_name;
create index FKhy3cd5se2avt08upapu19y1g6 on ups (perf_name_fk) ;

alter table patient_id add pat_name varchar(255);
update patient_id set pat_name = (
    select person_name.alphabetic_name from person_name
        join patient on person_name.pk = patient.pat_name_fk
        where patient.pk = patient_id.patient_fk );
update patient_id set pat_name = '*'
    where pat_name is null;

-- part 2: shall be applied on stopped archive before starting 5.34
update patient_id set pat_name = (
    select person_name.alphabetic_name from person_name
        join patient on person_name.pk = patient.pat_name_fk
        where patient.pk = patient_id.patient_fk)
    where pat_name is null;
update patient_id set pat_name = '*'
    where pat_name is null;
alter table patient_id add constraint patient_id_pat_id_pat_name_key
    unique (pat_id, pat_name);

-- part 3: can be applied on already running archive 5.34
alter table patient_id
    alter pat_name set not null;
