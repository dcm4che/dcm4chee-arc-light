-- part 1: can be applied on archive running archive 5.22
alter table person_name add
    alphabetic_name varchar(255),
    ideographic_name varchar(255),
    phonetic_name varchar(255);

update person_name
set alphabetic_name = concat(family_name, '^', given_name, '^', middle_name, '^', name_prefix, '^', name_suffix, '^'),
    ideographic_name = concat(i_family_name, '^', i_given_name, '^', i_middle_name, '^', i_name_prefix, '^', i_name_suffix, '^'),
    phonetic_name = concat(p_family_name, '^', p_given_name, '^', p_middle_name, '^', p_name_prefix, '^', p_name_suffix, '^');

--sqlserver requires additional column to be created upper(alphabetic_name) and index applied on this new column for ALPHABETIC_NAME
create index alphabetic_name_upper_idx on person_name (upper(alphabetic_name));
--sqlserver requires additional column to be created upper(alphabetic_name) and index applied on this new column for ALPHABETIC_NAME

drop index UK_gknfxd1vh283cmbg8ymia9ms8 on issuer;
create index UK_gknfxd1vh283cmbg8ymia9ms8 on issuer (entity_id);

alter table series
    add receiving_aet varchar(255),
        receiving_pres_addr varchar(255),
        sending_aet varchar(255),
        sending_pres_addr varchar(255);

update series set sending_aet = src_aet;

create index UK_b9e2bptvail8xnmb62h30h4d2 on series (sending_aet);
create index UK_lnck3a2qjo1vc430n1sy51vbr on series (receiving_aet);
create index UK_gxun7s005k8qf7qwhjhkkkkng on series (sending_pres_addr);
create index UK_e15a6qnq8jcq931agc2v48nvt on series (receiving_pres_addr);

-- part 2: shall be applied on stopped archive before starting 5.23
update person_name
set alphabetic_name = concat(family_name, '^', given_name, '^', middle_name, '^', name_prefix, '^', name_suffix, '^'),
    ideographic_name = concat(i_family_name, '^', i_given_name, '^', i_middle_name, '^', i_name_prefix, '^', i_name_suffix, '^'),
    phonetic_name = concat(p_family_name, '^', p_given_name, '^', p_middle_name, '^', p_name_prefix, '^', p_name_suffix, '^')
where alphabetic_name is null;

update series set sending_aet = src_aet where sending_aet is null;

-- part 3: can be applied on already running archive 5.23
--[22001][8152] String or binary data would be truncated. / [S0000][3621] The statement has been terminated.
alter table person_name
    alter column alphabetic_name varchar not null;
alter table person_name
    alter column ideographic_name varchar not null;
alter table person_name
    alter column phonetic_name varchar not null;
--[22001][8152] String or binary data would be truncated. / [S0000][3621] The statement has been terminated.

create index UK_gs2yshbwu0gkd33yxyv13keoh on person_name (alphabetic_name);
create index UK_ala4l4egord8i2tjvjidoqd1s on person_name (ideographic_name);
create index UK_9nr8ddkp8enufvbn72esyw3n1 on person_name (phonetic_name);

drop index UK_mgrwrswyrk02s1kn86cvpix3m on person_name;
drop index UK_byvbmsx5w9jop12gdqldogbwm on person_name;
drop index UK_hop27c6p2aiabl0ei6rj7oohi on person_name;

drop index UK_l3prcvmx90pdclj84s6uvbblm on person_name;
drop index UK_tgh0ek52g7cpioire3qwdweoi on person_name;
drop index UK_lwnfdvx2cknj9ravec592642d on person_name;

drop index UK_2189yvio0mae92hjhgbfwqgvc on person_name;
drop index UK_6cn50unrp2u9xf6authiollrr on person_name;
drop index UK_kungbb1r2qtt9aq0vsb1l68y6 on person_name;

alter table person_name drop column family_name;
alter table person_name drop column given_name;
alter table person_name drop column middle_name;
alter table person_name drop column name_prefix;
alter table person_name drop column name_suffix;
alter table person_name drop column i_family_name;
alter table person_name drop column i_given_name;
alter table person_name drop column i_middle_name;
alter table person_name drop column i_name_prefix;
alter table person_name drop column i_name_suffix;
alter table person_name drop column p_family_name;
alter table person_name drop column p_given_name;
alter table person_name drop column p_middle_name;
alter table person_name drop column p_name_prefix;
alter table person_name drop column p_name_suffix;

alter table series drop column src_aet;