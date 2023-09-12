ALTER TABLE person_name ADD upper_alphabetic_name AS UPPER(alphabetic_name)
CREATE INDEX alphabetic_name_upper_idx ON person_name (upper_alphabetic_name);

ALTER TABLE series ADD upper_series_desc AS UPPER(series_desc)
CREATE INDEX series_desc_upper_idx ON series (upper_series_desc);
	
ALTER TABLE study ADD upper_study_desc AS UPPER(study_desc)
CREATE INDEX study_desc_upper_idx ON study (upper_study_desc);
