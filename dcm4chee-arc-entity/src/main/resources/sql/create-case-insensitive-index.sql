create index alphabetic_name_upper_idx on person_name (upper(alphabetic_name));

create index series_desc_upper_idx on series (upper(series_desc));
create index study_desc_upper_idx on study (upper(study_desc));
