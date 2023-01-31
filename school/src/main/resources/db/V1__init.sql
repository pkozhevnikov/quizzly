
create table quizfact (
  id varchar primary key,
  title varchar not null,
  obsolete boolean not null default false,
  in_use boolean not null default false,
  ever_published boolean not null default false,
  is_published boolean not null default false,
  recommended_length int not null
);

create table exam (
  id varchar primary key,
  quiz_id varchar not null,
  quiz_title varchar not null,
  host_id varchar not null,
  host_name varchar not null,
  trial_length int not null,
  prestart_at timestamp not null,
  start_at timestamp not null,
  end_at timestamp not null,
  state varchar not null,
  cancelled_at timestamp
);

create table testee (
  exam_id varchar not null,
  testee_id varchar not null,
  testee_name varchar not null,
  testee_place varchar not null,
  constraint pk_testee primary key (exam_id,testee_id),
  constraint fk_testee_exam foreign key (exam_id) references exam(id) on delete cascade
);

