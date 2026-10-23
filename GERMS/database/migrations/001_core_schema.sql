-- GERMS 001: core schema (Phase 1-2). Run migrations in order in the Supabase SQL editor.
create extension if not exists pgcrypto;
create schema if not exists private;  -- not exposed through the API

-- ---------- enums ----------
create type user_role as enum ('admin','responder');
create type incident_status as enum ('draft','published','acknowledged','mobilizing','active_response','stabilized','closed','archived');
create type incident_severity as enum ('low','moderate','high','critical');
create type incident_category as enum ('flood','typhoon','fire','landslide','vehicular_accident','medical_emergency','search_and_rescue','mass_casualty','evacuation','relief_operation','other');
create type response_decision as enum ('accepted','declined');
create type assignment_status as enum ('assigned','preparing','en_route','arrived','on_task','completed','released','cancelled');
create type availability_status as enum ('available','standby','unavailable','off_duty');

create or replace function private.touch_updated_at() returns trigger language plpgsql as $$
begin new.updated_at = now(); return new; end $$;

-- ---------- identity ----------
create table profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  full_name text not null check (length(trim(full_name)) > 0),
  email text,
  phone text,
  role user_role not null default 'responder',
  is_active boolean not null default true,
  avatar_path text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create trigger trg_profiles_touch before update on profiles for each row execute function private.touch_updated_at();

create table service_divisions (
  id uuid primary key default gen_random_uuid(),
  code text not null unique,
  name text not null
);
create table responder_classifications (
  id uuid primary key default gen_random_uuid(),
  division_id uuid not null references service_divisions(id),
  code text not null unique,
  name text not null,
  requires_certification boolean not null default false
);
create table certifications (
  id uuid primary key default gen_random_uuid(),
  code text not null unique,
  name text not null,
  validity_months int check (validity_months is null or validity_months > 0)
);
create table profile_divisions (
  profile_id uuid not null references profiles(id) on delete cascade,
  division_id uuid not null references service_divisions(id),
  primary key (profile_id, division_id)
);
create table profile_classifications (
  profile_id uuid not null references profiles(id) on delete cascade,
  classification_id uuid not null references responder_classifications(id),
  verified_by uuid references profiles(id),
  verified_at timestamptz,
  primary key (profile_id, classification_id)
);
create table responder_qualifications (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid not null references profiles(id) on delete cascade,
  certification_id uuid not null references certifications(id),
  issued_on date,
  expires_on date,
  verified_by uuid references profiles(id),
  verified_at timestamptz,
  check (expires_on is null or issued_on is null or expires_on >= issued_on)
);
create index on responder_qualifications(profile_id);

create table responder_availability (
  profile_id uuid primary key references profiles(id) on delete cascade,
  status availability_status not null default 'available',
  note text,
  updated_at timestamptz not null default now()
);
create table responder_locations (
  profile_id uuid primary key references profiles(id) on delete cascade,
  latitude numeric(9,6) not null check (latitude between -90 and 90),
  longitude numeric(9,6) not null check (longitude between -180 and 180),
  accuracy_m numeric,
  source text not null default 'manual' check (source in ('manual','gps','device')),
  sharing_enabled boolean not null default false,
  recorded_at timestamptz not null default now()
);

-- ---------- incidents ----------
create sequence incident_seq;
create table incidents (
  id uuid primary key default gen_random_uuid(),
  incident_no text not null unique default ('GERMS-' || to_char(now(),'YYYY') || '-' || lpad(nextval('incident_seq')::text,5,'0')),
  title text not null check (length(trim(title)) > 0),
  description text,
  category incident_category not null default 'other',
  severity incident_severity not null default 'moderate',
  status incident_status not null default 'draft',
  reported_at timestamptz not null default now(),
  location_text text,
  barangay text,
  district text,
  city text not null default 'Davao City',
  latitude numeric(9,6) check (latitude between -90 and 90),
  longitude numeric(9,6) check (longitude between -180 and 180),
  hazards text,
  access_restrictions text,
  personnel_requested int not null default 0 check (personnel_requested >= 0),
  equipment_notes text,
  staging_location text,
  instructions text,
  incident_commander_id uuid references profiles(id),
  is_public boolean not null default false,
  public_summary text,
  closure_summary text,
  created_by uuid not null references profiles(id),
  published_by uuid references profiles(id),
  published_at timestamptz,
  closed_by uuid references profiles(id),
  closed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (status in ('draft','archived') or published_at is not null)
);
create index on incidents(status);
create index on incidents(severity);
create trigger trg_incidents_touch before update on incidents for each row execute function private.touch_updated_at();

create table incident_requirements (
  id uuid primary key default gen_random_uuid(),
  incident_id uuid not null references incidents(id) on delete cascade,
  division_id uuid not null references service_divisions(id),
  classification_id uuid references responder_classifications(id),
  headcount int not null default 1 check (headcount > 0)
);
create index on incident_requirements(incident_id);

create table incident_updates (   -- append-only history
  id uuid primary key default gen_random_uuid(),
  incident_id uuid not null references incidents(id) on delete cascade,
  author_id uuid references profiles(id),
  kind text not null check (kind in ('status_change','note','field_update','command')),
  message text,
  from_status incident_status,
  to_status incident_status,
  created_at timestamptz not null default now()
);
create index on incident_updates(incident_id, created_at desc);

create table incident_responses (
  id uuid primary key default gen_random_uuid(),
  incident_id uuid not null references incidents(id) on delete cascade,
  responder_id uuid not null references profiles(id) on delete cascade,
  decision response_decision not null,
  note text,
  responded_at timestamptz not null default now(),
  unique (incident_id, responder_id)
);
create index on incident_responses(incident_id);

create table incident_assignments (
  id uuid primary key default gen_random_uuid(),
  incident_id uuid not null references incidents(id) on delete cascade,
  responder_id uuid not null references profiles(id),
  role_name text not null default 'Assigned Responder',
  status assignment_status not null default 'assigned',
  note text,
  assigned_by uuid not null references profiles(id),
  assigned_at timestamptz not null default now(),
  released_at timestamptz
);
-- one ACTIVE assignment per responder per incident; cross-incident rule is a system setting enforced in assign_responder()
create unique index uq_active_assignment on incident_assignments(incident_id, responder_id)
  where status not in ('completed','released','cancelled');
create index on incident_assignments(responder_id);

create table assignment_status_history (
  id uuid primary key default gen_random_uuid(),
  assignment_id uuid not null references incident_assignments(id) on delete cascade,
  from_status assignment_status,
  to_status assignment_status not null,
  changed_by uuid references profiles(id),
  note text,
  changed_at timestamptz not null default now()
);
create index on assignment_status_history(assignment_id);

create table incident_command_positions (
  id uuid primary key default gen_random_uuid(),
  code text not null unique,
  name text not null,
  sort_order int not null default 0
);
create table incident_command_assignments (
  id uuid primary key default gen_random_uuid(),
  incident_id uuid not null references incidents(id) on delete cascade,
  position_id uuid not null references incident_command_positions(id),
  profile_id uuid not null references profiles(id),
  assigned_by uuid references profiles(id),
  assigned_at timestamptz not null default now(),
  unique (incident_id, position_id, profile_id)
);

-- ---------- lifecycle rules as data ----------
create table incident_status_transitions (
  from_status incident_status not null,
  to_status incident_status not null,
  primary key (from_status, to_status)
);
create table assignment_status_transitions (
  from_status assignment_status not null,
  to_status assignment_status not null,
  responder_allowed boolean not null default true,
  primary key (from_status, to_status)
);

-- ---------- notifications (three distinct states: created / observed / acknowledged) ----------
create table notifications (
  id uuid primary key default gen_random_uuid(),
  recipient_id uuid not null references profiles(id) on delete cascade,
  incident_id uuid references incidents(id) on delete cascade,
  kind text not null check (kind in ('incident_published','assignment','status_change','announcement')),
  priority text not null default 'normal' check (priority in ('normal','high','critical')),
  title text not null,
  body text,
  created_at timestamptz not null default now()
);
create index on notifications(recipient_id, created_at desc);
create table notification_receipts (
  notification_id uuid primary key references notifications(id) on delete cascade,
  observed_at timestamptz,
  acknowledged_at timestamptz
);

-- ---------- audit, settings, guest reports ----------
create table audit_logs (
  id bigint generated always as identity primary key,
  actor_id uuid,
  action text not null,
  entity text not null,
  entity_id text,
  details jsonb,
  created_at timestamptz not null default now()
);
create index on audit_logs(created_at desc);
create or replace function private.block_audit_mutation() returns trigger language plpgsql as $$
begin raise exception 'audit_logs is append-only'; end $$;
create trigger trg_audit_immutable before update or delete on audit_logs for each row execute function private.block_audit_mutation();

create table system_settings (
  key text primary key,
  value jsonb not null,
  description text,
  updated_by uuid references profiles(id),
  updated_at timestamptz not null default now()
);

create table guest_reports (
  id uuid primary key default gen_random_uuid(),
  reporter_name text check (length(reporter_name) <= 120),
  contact text check (length(contact) <= 120),
  description text not null check (length(description) between 10 and 2000),
  location_text text check (length(location_text) <= 300),
  latitude numeric(9,6) check (latitude between -90 and 90),
  longitude numeric(9,6) check (longitude between -180 and 180),
  status text not null default 'unverified' check (status in ('unverified','verified','rejected','converted')),
  reviewed_by uuid references profiles(id),
  reviewed_at timestamptz,
  created_at timestamptz not null default now()
);
