alter table study add expiration_date varchar(255);
alter table series add expiration_date varchar(255);
create table id_sequence (name varchar(255) not null, next_value int4 not null, version int8, primary key (name));

create index UK_ta3pi6exqft5encv389hwjytw on series (expiration_date);
create index UK_mlk5pdi8une92kru8g2ppappx on study (expiration_date);
