-- v5: user-created events. An event is a gathering hosted by a user at a place and time that
-- others discover and (from Milestone F) register for. An event also doubles as a checkpoint:
-- its `category` reuses the checkpoint point table, so checking in at an event rides the existing
-- check-in/points machinery instead of a parallel rewards system.
create table events (
    id                uuid primary key,
    host_id           uuid not null references users (id) on delete cascade,
    title             text not null,
    description       text not null default '',
    category          text not null,                 -- PARK | BEACH | VIEWPOINT | LANDMARK (checkpoint categories)
    venue_name        text not null default '',
    lat               double precision not null,
    lng               double precision not null,
    starts_at         timestamptz not null,
    ends_at           timestamptz,                   -- null = open-ended
    capacity          integer,                       -- null = unlimited
    visibility        text not null default 'PUBLIC',        -- PUBLIC | FRIENDS | PRIVATE
    registration_type text not null default 'NONE',          -- NONE | FREE | PAID
    price_cents       integer,                       -- paid only, in minor currency units
    currency          text,                          -- paid only, ISO-4217
    status            text not null default 'PUBLISHED',      -- DRAFT | PUBLISHED | CANCELLED
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    check (capacity is null or capacity > 0),
    check (price_cents is null or price_cents >= 0),
    check (visibility in ('PUBLIC', 'FRIENDS', 'PRIVATE')),
    check (registration_type in ('NONE', 'FREE', 'PAID')),
    check (status in ('DRAFT', 'PUBLISHED', 'CANCELLED')),
    -- Price and currency are present exactly when the event is paid.
    check ((registration_type = 'PAID') = (price_cents is not null and currency is not null)),
    check (ends_at is null or ends_at > starts_at)
);

-- Discovery scans a lat/lng bounding box of upcoming, published events; the host view lists by host.
create index idx_events_discovery on events (status, starts_at, lat, lng);
create index idx_events_host on events (host_id, starts_at desc);
