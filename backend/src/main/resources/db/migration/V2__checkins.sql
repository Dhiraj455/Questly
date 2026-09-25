-- Server-owned check-in ledger. The idempotency key is unique per account so retries can never
-- award points twice.
create table checkins (
    id              uuid primary key,
    user_id         uuid not null references users (id) on delete cascade,
    checkpoint_id   text not null,
    title           text not null,
    points          integer not null check (points >= 0),
    client_lat      double precision not null,
    client_lng      double precision not null,
    client_timestamp timestamptz not null,
    created_at      timestamptz not null default now(),
    idempotency_key uuid not null,
    unique (user_id, idempotency_key)
);

create index idx_checkins_user_created on checkins (user_id, created_at desc, id desc);
create index idx_checkins_user_checkpoint_created on checkins (user_id, checkpoint_id, created_at desc);
