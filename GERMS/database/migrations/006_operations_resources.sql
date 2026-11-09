-- GERMS 006: command-center resource data used by the Phase 2 operations views.
-- Run after migrations 001-005.

create table evacuation_centers (
  id uuid primary key default gen_random_uuid(),
  name text not null check (length(trim(name)) > 0),
  address text not null,
  capacity integer not null check (capacity >= 0),
  occupancy integer not null default 0 check (occupancy >= 0 and occupancy <= capacity),
  status text not null default 'open' check (status in ('open','full','closed','maintenance')),
  latitude numeric(9,6) check (latitude between -90 and 90),
  longitude numeric(9,6) check (longitude between -180 and 180),
  notes text,
  created_by uuid not null references profiles(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index evacuation_centers_status_idx on evacuation_centers(status);
create trigger trg_evacuation_centers_touch before update on evacuation_centers
  for each row execute function private.touch_updated_at();

create table disaster_resources (
  id uuid primary key default gen_random_uuid(),
  name text not null check (length(trim(name)) > 0),
  resource_type text not null,
  quantity numeric(12,2) not null check (quantity >= 0),
  unit text not null,
  status text not null default 'available' check (status in ('available','reserved','depleted','expired')),
  notes text,
  created_by uuid not null references profiles(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index disaster_resources_status_idx on disaster_resources(status);
create trigger trg_disaster_resources_touch before update on disaster_resources
  for each row execute function private.touch_updated_at();

alter table evacuation_centers enable row level security;
alter table disaster_resources enable row level security;

grant select on evacuation_centers, disaster_resources to authenticated;
grant insert, update on evacuation_centers, disaster_resources to authenticated;

create policy evacuation_centers_staff_read on evacuation_centers
  for select to authenticated using (true);
create policy evacuation_centers_admin_write on evacuation_centers
  for all to authenticated
  using (public.is_admin()) with check (public.is_admin());
create policy disaster_resources_staff_read on disaster_resources
  for select to authenticated using (true);
create policy disaster_resources_admin_write on disaster_resources
  for all to authenticated
  using (public.is_admin()) with check (public.is_admin());
