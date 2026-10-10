-- v5 Milestone F: registration & participation. One row per (event, user). Capacity is enforced in
-- the service: a REGISTERED count at/over the event's capacity puts newcomers on the WAITLIST, and
-- cancelling a REGISTERED spot auto-promotes the oldest waitlisted entry. ATTENDED is set when the
-- user checks in at the event (events ride the existing check-in machinery).
create table event_registrations (
    id            uuid primary key,
    event_id      uuid not null references events (id) on delete cascade,
    user_id       uuid not null references users (id) on delete cascade,
    status        text not null,  -- REGISTERED | WAITLISTED | CANCELLED | ATTENDED
    registered_at timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    unique (event_id, user_id),
    check (status in ('REGISTERED', 'WAITLISTED', 'CANCELLED', 'ATTENDED'))
);

-- Roster + count queries filter by event and status; the waitlist promotes in registration order.
create index idx_event_regs_event on event_registrations (event_id, status, registered_at);
-- "My registrations" / Going badge looks up by user.
create index idx_event_regs_user on event_registrations (user_id, status);
