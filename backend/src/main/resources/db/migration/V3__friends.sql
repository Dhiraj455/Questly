-- Social graph for v4. Friend codes let users add each other without exposing emails.

alter table users add column friend_code text unique;

-- Outstanding (pending) friend requests. Accepting one creates a friendship row and deletes it.
create table friend_requests (
    id            uuid primary key,
    requester_id  uuid not null references users (id) on delete cascade,
    addressee_id  uuid not null references users (id) on delete cascade,
    created_at    timestamptz not null default now(),
    unique (requester_id, addressee_id),
    check (requester_id <> addressee_id)
);
create index idx_friend_requests_addressee on friend_requests (addressee_id, created_at desc);

-- Accepted friendships, one row per pair with the smaller UUID first so (a,b) == (b,a).
create table friendships (
    user_low   uuid not null references users (id) on delete cascade,
    user_high  uuid not null references users (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (user_low, user_high),
    check (user_low < user_high)
);
create index idx_friendships_low on friendships (user_low);
create index idx_friendships_high on friendships (user_high);
