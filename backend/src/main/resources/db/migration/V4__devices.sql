-- FCM registration tokens for push notifications. One row per device; a token maps to one account
-- (reassigned if the same device signs into a different account).
create table device_tokens (
    token      text primary key,
    user_id    uuid not null references users (id) on delete cascade,
    created_at timestamptz not null default now()
);
create index idx_device_tokens_user on device_tokens (user_id);
