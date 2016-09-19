create table ext_retrieve_aet (pk  bigserial not null, retrieve_aet varchar(255) not null, instance_fk int8, primary key (pk));
create table study_ext_retrieve_aet (pk  bigserial not null, retrieve_aet varchar(255) not null, view_id varchar(255) not null, study_fk int8 not null, primary key (pk));

create index UK_fgmko2crqhp0rbvahgba8ouaa on ext_retrieve_aet (retrieve_aet);
create index UK_6ry2squ4qcv129lxpae1oy93m on study (created_time);
create index UK_fre5lje3j468v5v9vik6o89fa on study_ext_retrieve_aet (view_id);
create index UK_9bjf166lbmyirre66uoy96wag on study_ext_retrieve_aet (retrieve_aet);

alter table ext_retrieve_aet add constraint FK_h5738j9g4vrxxh0n06v74f9pq foreign key (instance_fk) references instance;
alter table study_ext_retrieve_aet add constraint FK_4ewtjnrk7hiy1dhypmvxrmeyc foreign key (study_fk) references study;

create index FK_h5738j9g4vrxxh0n06v74f9pq on ext_retrieve_aet (instance_fk) ;
create index FK_4ewtjnrk7hiy1dhypmvxrmeyc on study_ext_retrieve_aet (study_fk) ;

create sequence ext_retrieve_aet_pk_seq;
create sequence study_ext_retrieve_aet_pk_seq;
