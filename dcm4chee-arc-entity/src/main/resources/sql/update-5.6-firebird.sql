alter table location add multi_ref integer, add uidmap_fk numeric(18,0), add object_type integer;
update location set object_type = 0;
alter table location alter object_type set not null;
alter table location alter tsuid drop not null;
create table uidmap (pk numeric(18,0) not null, uidmap blob not null, primary key (pk));
create index UK_i1lnahmehau3r3j9pdyxg3p3y on location (multi_ref);
alter table location add constraint FK_bfk5vl6eoxaf0hhwiu3rbgmkn foreign key (uidmap_fk) references uidmap;

--to be checked--
create index FK_bfk5vl6eoxaf0hhwiu3rbgmkn on location (uidmap_fk) ;
--to be checked--

create generator uidmap_pk_seq;
