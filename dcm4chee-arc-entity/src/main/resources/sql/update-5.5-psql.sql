alter table study add exipiration_date varchar(255);
alter table series add exipiration_date varchar(255);

create index UK_ta3pi6exqft5encv389hwjytw on series (expiration_date);
create index UK_mlk5pdi8une92kru8g2ppappx on study (expiration_date);
