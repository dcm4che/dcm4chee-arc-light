alter table content_item drop foreign key if exists FKfra6ee5jtybfp94ldpvva623o;
alter table content_item drop foreign key if exists FK7rpy6unnb5b18b8ieuqr9w9i9;
alter table content_item drop foreign key if exists FKei15n1lk1h1e8f89e9ubalm7q;
alter table global_subscription drop foreign key if exists FKqkchf2ue2j1p3fv2o94rhacvi;
alter table hl7psu_task drop foreign key if exists FKlvjl0o0sdhht440wccag722e4;
alter table ian_task drop foreign key if exists FKs3re94tlv0dbd5go33mwdk6dr;
alter table instance drop foreign key if exists FKokrgkyvch35m6iwr309mawsna;
alter table instance drop foreign key if exists FKqmk050ojwer9ujsbl74d4kf6e;
alter table instance drop foreign key if exists FKe92yganh695k2y4hxfqxtesg1;
alter table instance_req drop foreign key if exists FK8leut3c0gfxcg6y3lbjlpt2bt;
alter table instance_req drop foreign key if exists FKmbg8j00cvubj76doba8funq8j;
alter table location drop foreign key if exists FKpp9xjy946uspo3piayc6ofels;
alter table location drop foreign key if exists FKendncyyv928u41dfo1x09s644;
alter table mpps drop foreign key if exists FKnhstgtx2w1xd61u4iqbn731pu;
alter table mpps drop foreign key if exists FK2m3l9bh173bmq88ukf81yd9ow;
alter table mpps drop foreign key if exists FKs7mb8qsr1mjijnw9bvx0dhqab;
alter table mwl_item drop foreign key if exists FK9vjk41q61e9x9n4qfve6jud8s;
alter table mwl_item drop foreign key if exists FKqui6rvqyg1qfujae4mw3o0nbm;
alter table mwl_item drop foreign key if exists FKpl6u7oyyp6o3ca8r9jbxv5oge;
alter table mwl_item drop foreign key if exists FKeursgxiy2u6ws39cgn1b25e6k;
alter table mwl_item drop foreign key if exists FKf5l3x4bfqn3jv8mhj617kidx7;
alter table patient drop foreign key if exists FKe8j5c2rhq67rt3opqfggoplt2;
alter table patient drop foreign key if exists FKc66cfkdwiu7eq65kgmxilrocw;
alter table patient drop foreign key if exists FKig1mwm5jpkyq6733t0hrf56g;
alter table patient drop foreign key if exists FKjh4miooi709vj5l6xi8h95f3e;
alter table patient_id drop foreign key if exists FKih8d22x50j7orytfmigrnssea;
alter table rejected_instance drop foreign key if exists FK27xpx6okj63sunyo76tf0s913;
alter table rel_study_pcode drop foreign key if exists FKlia0q74j0xhj2u0avy1wbbx7q;
alter table rel_study_pcode drop foreign key if exists FKta5515ogakmavm1swi6tw1n2n;
alter table rel_task_dicomattrs drop foreign key if exists FK53sqs6uiua7ff45u94lrv50t7;
alter table rel_task_dicomattrs drop foreign key if exists FKhd3dj3u43xxgdxn7w5imp5ank;
alter table rel_ups_perf_code drop foreign key if exists FK6om2durembdfk2rmd29dlcm2t;
alter table rel_ups_perf_code drop foreign key if exists FK31cp9ux8xb2dcu0nv84tgd8um;
alter table rel_ups_station_class_code drop foreign key if exists FK62f8dua3p8bxv3vilctc7pd42;
alter table rel_ups_station_class_code drop foreign key if exists FKf0y92ufejerf9dwv8sl6kdkae;
alter table rel_ups_station_location_code drop foreign key if exists FKp5d05caciw2cqik5uqt7r8w6p;
alter table rel_ups_station_location_code drop foreign key if exists FKlxb9i1b48mfxo0y72fpeoxl48;
alter table rel_ups_station_name_code drop foreign key if exists FK308ga4grefjjwmrwtsirjmbhh;
alter table rel_ups_station_name_code drop foreign key if exists FKd4gjwmhmlphp865fxiqjypwo2;
alter table series drop foreign key if exists FKtih7mdqwcklym58jd9wn3itjq;
alter table series drop foreign key if exists FK6nn55bx7pqlmimgfnhfpjra94;
alter table series drop foreign key if exists FKnpdeeclrflvuxajeqjmk6utf;
alter table series drop foreign key if exists FK84fpblugeadm5u6dw1idi80a0;
alter table series drop foreign key if exists FK5p2gub2u40fyh2ch6bp6nan4l;
alter table series drop foreign key if exists FK3196nws7ngapmsmtoafb4e88j;
alter table series_query_attrs drop foreign key if exists FKsk791fnscydy7whj3lvrp3j1a;
alter table series_req drop foreign key if exists FK15lid2bqbrsmjatxiq0vug4wq;
alter table series_req drop foreign key if exists FK1gs2u7w7uefvi7dcjg9vlp23x;
alter table soundex_code drop foreign key if exists FKctukwjaer0axw2jwv3bfj67r2;
alter table sps_station_aet drop foreign key if exists FKmeklscb7t55i8mb2d4cjucywd;
alter table study drop foreign key if exists FK2ktu3hbxxalrngfhyp4j6pf70;
alter table study drop foreign key if exists FKocnq3so9ej7h0mnd6a6arjf6c;
alter table study drop foreign key if exists FKndwmntu3lsjd78erctnbposyr;
alter table study_query_attrs drop foreign key if exists FKmu32jq25y6qkimi7hn7d4s2hp;
alter table subscription drop foreign key if exists FKh9mdhriyx32gr91pfdgnl48f0;
alter table ups drop foreign key if exists FKaddby69vtwxpssgaa12ct6pn3;
alter table ups drop foreign key if exists FKhy3cd5se2avt08upapu19y1g6;
alter table ups drop foreign key if exists FKjcmu4x6x02r0tc1d28xb3nf17;
alter table ups drop foreign key if exists FKr3t1gbo2e42oleaqeastvv5ej;
alter table ups_req drop foreign key if exists FK1b5veu90oftv8o95nxx2xndpb;
alter table ups_req drop foreign key if exists FKh1sjrdq663dnk8j7hsthun4ie;
alter table verify_observer drop foreign key if exists FK8vo3f94xgum0qajsjqnlnqjo4;
alter table verify_observer drop foreign key if exists FKkpcwkrsipamdg81mao7nkmdc2;
drop table if exists code;
drop table if exists content_item;
drop table if exists dicomattrs;
drop table if exists global_subscription;
drop table if exists hl7psu_task;
drop table if exists ian_task;
drop table if exists id_sequence;
drop table if exists instance;
drop table if exists instance_req;
drop table if exists key_value2;
drop table if exists location;
drop table if exists metadata;
drop table if exists mpps;
drop table if exists mwl_item;
drop table if exists patient;
drop table if exists patient_demographics;
drop table if exists patient_id;
drop table if exists person_name;
drop table if exists rejected_instance;
drop table if exists rel_study_pcode;
drop table if exists rel_task_dicomattrs;
drop table if exists rel_ups_perf_code;
drop table if exists rel_ups_station_class_code;
drop table if exists rel_ups_station_location_code;
drop table if exists rel_ups_station_name_code;
drop table if exists series;
drop table if exists series_query_attrs;
drop table if exists series_req;
drop table if exists soundex_code;
drop table if exists sps_station_aet;
drop table if exists stgcmt_result;
drop table if exists study;
drop table if exists study_query_attrs;
drop table if exists subscription;
drop table if exists task;
drop table if exists uidmap;
drop table if exists ups;
drop table if exists ups_req;
drop table if exists verify_observer;
