-- part 1: can be applied on archive running archive 5.30
alter table patient_id
    add patient_fk numeric(18,0);

alter table patient_id
    add constraint FK_hba9466n4re9re8id3c8abmnv foreign key (patient_fk) references patient;

update patient_id set patient_fk = (
    select patient.pk from patient where patient_id_fk = patient_id.pk );

-- part 2: shall be applied on stopped archive before starting 5.30
update patient_id set patient_fk = (
    select patient.pk from patient where patient_id_fk = patient_id.pk )
where patient_fk is null;

-- part 3: can be applied on already running archive 5.30
alter table patient drop patient_id_fk;
