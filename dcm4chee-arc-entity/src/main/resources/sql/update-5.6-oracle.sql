alter table location add multi_ref number(10,0), add uidmap_fk number(19,0), add object_type number(10,0);
update location set object_type = 0;
alter table location modify object_type not null;
alter table location modify tsuid drop not null;
create table uidmap (pk number(19,0) not null, uidmap blob not null, primary key (pk));
create index UK_i1lnahmehau3r3j9pdyxg3p3y on location (multi_ref);
alter table location add constraint FK_bfk5vl6eoxaf0hhwiu3rbgmkn foreign key (uidmap_fk) references uidmap;

--to be checked--
create index FK_bfk5vl6eoxaf0hhwiu3rbgmkn on location (uidmap_fk) ;
--to be checked--

create sequence uidmap_pk_seq;
