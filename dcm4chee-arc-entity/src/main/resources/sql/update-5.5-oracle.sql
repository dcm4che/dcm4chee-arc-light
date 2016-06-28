alter table study add expiration_date varchar2(255);
alter table series add expiration_date varchar2(255);
create table id_sequence (name varchar2(255 char) not null, next_value number(10,0) not null, version number(19,0), primary key (name));

create index UK_ta3pi6exqft5encv389hwjytw on series (expiration_date);
create index UK_mlk5pdi8une92kru8g2ppappx on study (expiration_date);
