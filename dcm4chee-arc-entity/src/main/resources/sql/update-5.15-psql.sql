-- can be applied on running archive 5.14
alter table patient
  add verification_status int4,
  add failed_verifications int4,
  add verification_time timestamp;

alter table stgcmt_result
  add batch_id varchar(255);

create index UK_e7rsyrt9n2mccyv1fcd2s6ikv on patient (verification_status);
create index UK_bay8wkvwegw3pmyeypv2v93k1 on patient (verification_time);

create index UK_f718gnu5js0mdg39q6j7fklia on stgcmt_result (batch_id);

-- may be already applied on running archive 5.14 to minimize downtime
-- and re-applied on stopped archive only on patients inserted after the previous update (where patient.pk > xxx)
update patient set verification_status = 0, failed_verifications = 0;

-- shall be applied on stopped or running archive 5.15
alter table patient
  alter verification_status set not null,
  alter failed_verifications set not null;
