alter table issuer drop constraint UK_gknfxd1vh283cmbg8ymia9ms8;
create unique index UK_gknfxd1vh283cmbg8ymia9ms8 on issuer(entity_id) where entity_id is not null;
alter table issuer drop constraint UK_t1p7jajas0mu12sx8jvtp2y0f;
create unique index UK_t1p7jajas0mu12sx8jvtp2y0f on issuer(entity_uid, entity_uid_type) where entity_id is not null and entity_uid_type is not null;
alter table patient_id drop constraint UK_31gvi9falc03xs94m8l3pgoid;
create unique index UK_31gvi9falc03xs94m8l3pgoid on patient_id(pat_id, issuer_fk) where pat_id is not null;
