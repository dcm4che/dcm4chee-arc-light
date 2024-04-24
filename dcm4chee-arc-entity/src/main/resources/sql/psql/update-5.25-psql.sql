-- part 1: can be applied on archive running archive 5.24
create table patient_demographics
(
    pat_id        varchar(255) not null,
    pat_birthdate varchar(255),
    pat_name      varchar(255),
    pat_sex       varchar(255),
    primary key (pat_id)
);
