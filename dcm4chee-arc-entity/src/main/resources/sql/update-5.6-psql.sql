alter table location add multi_ref int4, add uidmap_fk int8;
create table uidmap (pk int8 not null, uidmap bytea not null, primary key (pk));
create index UK_i1lnahmehau3r3j9pdyxg3p3y on location (multi_ref);
alter table location add constraint FK_bfk5vl6eoxaf0hhwiu3rbgmkn foreign key (uidmap_fk) references uidmap;
create index FK_bfk5vl6eoxaf0hhwiu3rbgmkn on location (uidmap_fk) ;
create sequence uidmap_pk_seq;
alter table location ALTER COLUMN multi_ref set DATA TYPE INT8
