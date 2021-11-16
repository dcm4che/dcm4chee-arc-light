-- can be applied on archive running archive 5.24
create table patient_demographics
(
    pat_id varchar2(255 char) not null,
    pat_birthdate varchar2(255 char),
    pat_name varchar2(255 char),
    pat_sex varchar2(255 char),
    primary key (pat_id)
);
