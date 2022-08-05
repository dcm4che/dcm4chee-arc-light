-- can be applied on running archive 5.13
create table stgver_task (pk bigint not null, completed integer not null, created_time datetime not null, failed integer not null,
  local_aet varchar(255) not null, series_iuid varchar(255), sop_iuid varchar(255), storage_ids varchar(255),
  stgcmt_policy integer, study_iuid varchar(255) not null, update_location_status bit, updated_time datetime not null,
  queue_msg_fk bigint not null, primary key (pk));
alter table stgver_task add constraint UK_hch5fanx7ejwew2ag2ividq9r  unique (queue_msg_fk);
create index UK_fe2td8g77y54d90w7339ka0ix on stgver_task (created_time);
create index UK_bja5px1r9qts4nydp1a2i61ok on stgver_task (updated_time);
create index UK_iudr0qmrm15i2evq1733h1ace on stgver_task (study_iuid(64), series_iuid(64), sop_iuid(64));
alter table stgver_task add constraint FK_hch5fanx7ejwew2ag2ividq9r foreign key (queue_msg_fk) references queue_msg (pk);
alter table series
  add stgver_time datetime,
  add stgver_failures integer default 0 not null,
  add compress_time datetime,
  add compress_tsuid varchar(255),
  add compress_params varchar(255),
  add compress_failures integer default 0 not null;

create index UK_ftv3ijh2ud6ogoknneyqc6t9i on series (stgver_time);
create index UK_s1vceb8cu9c45j0q8tbldgol9 on series (stgver_failures);
create index UK_38mfgfnjhan2yhnwqtcrawe4 on series (compress_time);
create index UK_889438ocqfrvybu3k2eo65lpa on series (compress_failures);

create index FK_hch5fanx7ejwew2ag2ividq9r on stgver_task (queue_msg_fk);
