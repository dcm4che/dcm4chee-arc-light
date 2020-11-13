-- part 1: can be applied on archive running archive 5.22
alter table person_name
    add alphabetic_name varchar(255),
    add ideographic_name varchar(255),
    add phonetic_name varchar(255);

update person_name
set alphabetic_name = concat(family_name, '^', given_name, '^', middle_name, '^', name_prefix, '^', name_suffix),
    ideographic_name = concat(i_family_name, '^', i_given_name, '^', i_middle_name, '^', i_name_prefix, '^', i_name_suffix),
    phonetic_name = concat(p_family_name, '^', p_given_name, '^', p_middle_name, '^', p_name_prefix, '^', p_name_suffix);

create index UK_gs2yshbwu0gkd33yxyv13keoh on person_name (alphabetic_name);
create index UK_ala4l4egord8i2tjvjidoqd1s on person_name (ideographic_name);
create index UK_9nr8ddkp8enufvbn72esyw3n1 on person_name (phonetic_name);

create index alphabetic_name_upper_idx on person_name (upper(alphabetic_name));
create index ideographic_name_upper_idx on person_name (upper(ideographic_name));
create index phonetic_name_upper_idx on person_name (upper(phonetic_name));

alter table issuer drop constraint UK_gknfxd1vh283cmbg8ymia9ms8;
create index UK_gknfxd1vh283cmbg8ymia9ms8 on issuer (entity_id);

-- part 2: shall be applied on stopped archive before starting 5.23
update person_name
set alphabetic_name = concat(family_name, '^', given_name, '^', middle_name, '^', name_prefix, '^', name_suffix),
    ideographic_name = concat(i_family_name, '^', i_given_name, '^', i_middle_name, '^', i_name_prefix, '^', i_name_suffix),
    phonetic_name = concat(p_family_name, '^', p_given_name, '^', p_middle_name, '^', p_name_prefix, '^', p_name_suffix)
where alphabetic_name is null;

-- part 3: can be applied on already running archive 5.23
alter table person_name
    alter alphabetic_name set not null,
    alter ideographic_name set not null,
    alter phonetic_name set not null;

alter table person_name drop family_name, drop given_name, drop middle_name, drop name_prefix, drop name_suffix,
    drop i_family_name, drop i_given_name, drop i_middle_name, drop i_name_prefix, drop i_name_suffix,
    drop p_family_name, drop p_given_name, drop p_middle_name, drop p_name_prefix, drop p_name_suffix;