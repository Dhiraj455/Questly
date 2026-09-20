-- Baseline schema for Questly v3 auth. Additive migrations (V2, V3, ...) follow; never edit an
-- applied migration.

create table users (
    id             uuid primary key,
    email          text not null unique,
    display_name   text not null,
    password_hash  text,                                  -- null for OAuth-only accounts
    email_verified boolean not null default false,
    created_at     timestamptz not null default now()
);

create table email_tokens (
    token      text primary key,
    user_id    uuid not null references users (id) on delete cascade,
    purpose    text not null,                             -- VERIFY | RESET
    expires_at timestamptz not null,
    used_at    timestamptz
);

create table refresh_tokens (
    token      text primary key,
    user_id    uuid not null references users (id) on delete cascade,
    expires_at timestamptz not null,
    revoked_at timestamptz
);

create index idx_email_tokens_user on email_tokens (user_id);
create index idx_refresh_tokens_user on refresh_tokens (user_id);
