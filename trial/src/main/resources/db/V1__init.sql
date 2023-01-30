
create table quiz (
  id varchar primary key,
  title varchar,
  intro text,
  content bytea
);

create table exam (
  id varchar primary key,
  quiz_id varchar not null,
  start_at timestamp,
  end_at timestamp,
  trial_length int,
  constraint fk_exam_quiz foreign key (quiz_id) references quiz (id)
);

