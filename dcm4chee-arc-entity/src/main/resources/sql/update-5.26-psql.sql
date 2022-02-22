-- can be applied on archive running archive 5.25
alter table mpps
    add accno_entity_id       varchar(255),
    add accno_entity_uid      varchar(255),
    add accno_entity_uid_type varchar(255);

alter table mwl_item
    add accno_entity_id       varchar(255),
    add accno_entity_uid      varchar(255),
    add accno_entity_uid_type varchar(255),
    add admid_entity_id       varchar(255),
    add admid_entity_uid      varchar(255),
    add admid_entity_uid_type varchar(255);

alter table patient_id
    add entity_id       varchar(255),
    add entity_uid      varchar(255),
    add entity_uid_type varchar(255);

alter table series_req
    add accno_entity_id       varchar(255),
    add accno_entity_uid      varchar(255),
    add accno_entity_uid_type varchar(255);

alter table study
    add accno_entity_id       varchar(255),
    add accno_entity_uid      varchar(255),
    add accno_entity_uid_type varchar(255),
    add admid_entity_id       varchar(255),
    add admid_entity_uid      varchar(255),
    add admid_entity_uid_type varchar(255);

alter table ups
    add admid_entity_id       varchar(255),
    add admid_entity_uid      varchar(255),
    add admid_entity_uid_type varchar(255);

alter table ups_req
    add accno_entity_id       varchar(255),
    add accno_entity_uid      varchar(255),
    add accno_entity_uid_type varchar(255);

create index UK_tkyjkkxxhnr0fem7m0h3844jk on patient_id (pat_id);
create index UK_d1sdyupb0vwvx23jownjnyy72 on patient_id (entity_id);
create index UK_7ng2mv24lewubudyh6c8lnjee on patient_id (entity_uid);

update mpps
set (accno_entity_id, accno_entity_uid, accno_entity_uid_type) =
        (select issuer.entity_id, issuer.entity_uid, issuer.entity_uid_type
         from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null;

update mwl_item
set (accno_entity_id, accno_entity_uid, accno_entity_uid_type) =
        (select issuer.entity_id, issuer.entity_uid, issuer.entity_uid_type
         from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null;

update mwl_item
set (admid_entity_id, admid_entity_uid, admid_entity_uid_type) =
        (select issuer.entity_id, issuer.entity_uid, issuer.entity_uid_type
         from issuer where admid_issuer_fk = issuer.pk)
where admid_issuer_fk is not null;

update patient_id
set (entity_id, entity_uid, entity_uid_type) =
        (select issuer.entity_id, issuer.entity_uid, issuer.entity_uid_type
         from issuer where issuer_fk = issuer.pk)
where issuer_fk is not null;

update series_req
set (accno_entity_id, accno_entity_uid, accno_entity_uid_type) =
        (select issuer.entity_id, issuer.entity_uid, issuer.entity_uid_type
         from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null;

update study
set (accno_entity_id, accno_entity_uid, accno_entity_uid_type) =
        (select issuer.entity_id, issuer.entity_uid, issuer.entity_uid_type
         from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null;

update study
set (admid_entity_id, admid_entity_uid, admid_entity_uid_type) =
        (select issuer.entity_id, issuer.entity_uid, issuer.entity_uid_type
         from issuer where admid_issuer_fk = issuer.pk)
where admid_issuer_fk is not null;

update ups
set (admid_entity_id, admid_entity_uid, admid_entity_uid_type) =
        (select issuer.entity_id, issuer.entity_uid, issuer.entity_uid_type
         from issuer where ups.admission_issuer_fk = issuer.pk)
where admission_issuer_fk is not null;

update ups_req
set (accno_entity_id, accno_entity_uid, accno_entity_uid_type) =
        (select issuer.entity_id, issuer.entity_uid, issuer.entity_uid_type
         from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null;

-- part 2: shall be applied on stopped archive before starting 5.25
update mpps
set (accno_entity_id, accno_entity_uid, accno_entity_uid_type) =
        (select issuer.entity_id, issuer.entity_uid, issuer.entity_uid_type
         from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null
  and accno_entity_id is null
  and accno_entity_uid is null;

update mwl_item
set (accno_entity_id, accno_entity_uid, accno_entity_uid_type) =
        (select issuer.entity_id, issuer.entity_uid, issuer.entity_uid_type
         from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null
  and accno_entity_id is null
  and accno_entity_uid is null;

update mwl_item
set (admid_entity_id, admid_entity_uid, admid_entity_uid_type) =
        (select issuer.entity_id, issuer.entity_uid, issuer.entity_uid_type
         from issuer where admid_issuer_fk = issuer.pk)
where admid_issuer_fk is not null
  and admid_entity_id is null
  and admid_entity_uid is null;

update patient_id
set (entity_id, entity_uid, entity_uid_type) =
        (select issuer.entity_id, issuer.entity_uid, issuer.entity_uid_type
         from issuer where issuer_fk = issuer.pk)
where issuer_fk is not null
  and entity_id is null
  and entity_uid is null;

update series_req
set (accno_entity_id, accno_entity_uid, accno_entity_uid_type) =
        (select issuer.entity_id, issuer.entity_uid, issuer.entity_uid_type
         from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null
  and accno_entity_id is null
  and accno_entity_uid is null;

update study
set (accno_entity_id, accno_entity_uid, accno_entity_uid_type) =
        (select issuer.entity_id, issuer.entity_uid, issuer.entity_uid_type
         from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null
  and accno_entity_id is null
  and accno_entity_uid is null;

update study
set (admid_entity_id, admid_entity_uid, admid_entity_uid_type) =
        (select issuer.entity_id, issuer.entity_uid, issuer.entity_uid_type
         from issuer where admid_issuer_fk = issuer.pk)
where admid_issuer_fk is not null
  and admid_entity_id is null
  and admid_entity_uid is null;

update ups
set (admid_entity_id, admid_entity_uid, admid_entity_uid_type) =
        (select issuer.entity_id, issuer.entity_uid, issuer.entity_uid_type
         from issuer where ups.admission_issuer_fk = issuer.pk)
where admission_issuer_fk is not null
  and admid_entity_id is null
  and admid_entity_uid is null;

update ups_req
set (accno_entity_id, accno_entity_uid, accno_entity_uid_type) =
        (select issuer.entity_id, issuer.entity_uid, issuer.entity_uid_type
         from issuer where accno_issuer_fk = issuer.pk)
where accno_issuer_fk is not null
  and accno_entity_id is null
  and accno_entity_uid is null;

-- part 3: can be applied on already running archive 5.25
alter table mpps
    drop accno_issuer_fk;

alter table mwl_item
    drop accno_issuer_fk,
    drop admid_issuer_fk;

alter table patient_id
    drop issuer_fk;

alter table series_req
    drop accno_issuer_fk;

alter table study
    drop accno_issuer_fk,
    drop admid_issuer_fk;

alter table ups
    drop admission_issuer_fk;

alter table ups_req
    drop accno_issuer_fk;

drop table issuer;
drop sequence issuer_pk_seq;
