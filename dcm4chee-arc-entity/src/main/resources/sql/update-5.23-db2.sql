-- part 1: can be applied on archive running archive 5.22
alter table person_name add alphabetic_name varchar(255);
alter table person_name add ideographic_name varchar(255);
alter table person_name add phonetic_name varchar(255);

update person_name
set alphabetic_name = coalesce(family_name,'') || '^' || coalesce(given_name,'') || '^' || coalesce(middle_name,'')
                        || '^' || coalesce(name_prefix,'') || '^' || coalesce(name_suffix,'') || '^',
    ideographic_name = coalesce(i_family_name,'') || '^' || coalesce(i_given_name,'') || '^' || coalesce(i_middle_name,'')
                        || '^' || coalesce(i_name_prefix,'') || '^' || coalesce(i_name_suffix,'') || '^',
    phonetic_name = coalesce(p_family_name,'') || '^' || coalesce(p_given_name,'') || '^' || coalesce(p_middle_name,'')
                        || '^' || coalesce(p_name_prefix,'') || '^' || coalesce(p_name_suffix,'') || '^';

create index UK_gs2yshbwu0gkd33yxyv13keoh on person_name (alphabetic_name);
create index UK_ala4l4egord8i2tjvjidoqd1s on person_name (ideographic_name);
create index UK_9nr8ddkp8enufvbn72esyw3n1 on person_name (phonetic_name);

drop index UK_GKNFXD1VH283CMBG8YMIA9MS8;
create index UK_gknfxd1vh283cmbg8ymia9ms8 on issuer (entity_id);

-- part 2: shall be applied on stopped archive before starting 5.23
update person_name
set alphabetic_name = coalesce(family_name,'') || '^' || coalesce(given_name,'') || '^' || coalesce(middle_name,'')
                        || '^' || coalesce(name_prefix,'') || '^' || coalesce(name_suffix,'') || '^',
    ideographic_name = coalesce(i_family_name,'') || '^' || coalesce(i_given_name,'') || '^' || coalesce(i_middle_name,'')
                        || '^' || coalesce(i_name_prefix,'') || '^' || coalesce(i_name_suffix,'') || '^',
    phonetic_name = coalesce(p_family_name,'') || '^' || coalesce(p_given_name,'') || '^' || coalesce(p_middle_name,'')
                        || '^' || coalesce(p_name_prefix,'') || '^' || coalesce(p_name_suffix,'') || '^'
where alphabetic_name is null;

-- part 3: can be applied on already running archive 5.23
alter table person_name alter column alphabetic_name set not null;
alter table person_name alter column ideographic_name set not null;
alter table person_name alter column phonetic_name set not null;

create index alphabetic_name_upper_idx on person_name (upper(alphabetic_name));

alter table person_name drop family_name;
alter table person_name drop given_name;
alter table person_name drop middle_name;
alter table person_name drop name_prefix;
alter table person_name drop name_suffix;
alter table person_name drop i_family_name;
alter table person_name drop i_given_name;
alter table person_name drop i_middle_name;
alter table person_name drop i_name_prefix;
alter table person_name drop i_name_suffix;
alter table person_name drop p_family_name;
alter table person_name drop p_given_name;
alter table person_name drop p_middle_name;
alter table person_name drop p_name_prefix;
alter table person_name drop p_name_suffix;