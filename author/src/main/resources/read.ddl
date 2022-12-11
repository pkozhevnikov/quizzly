
create table quiz (
  id varchar primary key,
  title varchar not null,
  status varchar not null,
  obsolete boolean not null default false
);

create table member (
  id varchar not null,
  role int not null,
  person_id varchar not null,
  name varchar not null,
  constraint pk_member primary key (id,person_id),
  constraint fk_member_quiz foreign key (id) references quiz(id) on delete cascade
);


