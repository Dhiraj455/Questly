-- v5 Milestone H: chat. A conversation is either a 1:1 DM (DIRECT) between two friends, or a group
-- chat tied to an event (EVENT). Live delivery reuses the WebSocket hub; offline delivery reuses FCM.
create table conversations (
    id         uuid primary key,
    type       text not null,            -- DIRECT | EVENT
    event_id   uuid references events (id) on delete cascade,  -- set iff EVENT
    -- Stable key for a DIRECT pair ("<lowerUuid>:<higherUuid>") so a pair has exactly one DM. Null for EVENT.
    dm_key     text,
    created_at timestamptz not null default now(),
    check (type in ('DIRECT', 'EVENT')),
    check ((type = 'EVENT') = (event_id is not null)),
    check ((type = 'DIRECT') = (dm_key is not null))
);
create unique index idx_conversations_dm_key on conversations (dm_key) where dm_key is not null;
create unique index idx_conversations_event on conversations (event_id) where event_id is not null;

create table conversation_members (
    conversation_id uuid not null references conversations (id) on delete cascade,
    user_id         uuid not null references users (id) on delete cascade,
    joined_at       timestamptz not null default now(),
    last_read_at    timestamptz,
    muted           boolean not null default false,
    primary key (conversation_id, user_id)
);
create index idx_conv_members_user on conversation_members (user_id);

create table messages (
    id              uuid primary key,
    conversation_id uuid not null references conversations (id) on delete cascade,
    sender_id       uuid not null references users (id) on delete cascade,
    body            text not null,
    created_at      timestamptz not null default now()
);
create index idx_messages_conversation on messages (conversation_id, created_at desc, id desc);

-- Moderation (required for Play's UGC policy). Blocks are one-directional; reports are for triage.
create table user_blocks (
    blocker_id uuid not null references users (id) on delete cascade,
    blocked_id uuid not null references users (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (blocker_id, blocked_id),
    check (blocker_id <> blocked_id)
);

create table message_reports (
    id          uuid primary key,
    reporter_id uuid not null references users (id) on delete cascade,
    message_id  uuid not null references messages (id) on delete cascade,
    reason      text not null default '',
    created_at  timestamptz not null default now(),
    unique (reporter_id, message_id)
);
