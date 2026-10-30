-- GERMS 005: store the response-team reference data and incident assignments in Supabase.

create table response_teams (
  id uuid primary key default gen_random_uuid(),
  name text not null unique,
  is_active boolean not null default true
);

insert into response_teams(name) values
  ('Rescue Team Alpha'),
  ('Medical Team Bravo'),
  ('Logistics Team Charlie'),
  ('Water Rescue Team Delta');

alter table incidents
  add column response_team_id uuid references response_teams(id);

alter table response_teams enable row level security;
grant select on response_teams to authenticated;
create policy response_teams_select on response_teams
  for select to authenticated using (true);

create or replace function public.assign_incident_team(p_id uuid, p_team uuid) returns void
language plpgsql security definer set search_path = public as $$
declare v_status incident_status; v_team_name text;
begin
  perform private.require_admin();
  select name into v_team_name from response_teams where id = p_team and is_active;
  if v_team_name is null then raise exception 'Response team not found or inactive'; end if;

  select status into v_status from incidents where id = p_id for update;
  if v_status is null then raise exception 'Incident not found'; end if;
  if v_status in ('closed','archived') then raise exception 'Closed or archived incidents cannot be assigned'; end if;

  update incidents set response_team_id = p_team where id = p_id;
  insert into incident_updates(incident_id, author_id, kind, message)
    values (p_id, auth.uid(), 'note', 'Assigned to ' || v_team_name);
  perform private.audit('incident.assign_team','incident', p_id::text, jsonb_build_object('team', v_team_name));
end $$;

revoke execute on function public.assign_incident_team(uuid, uuid) from public, anon;
grant execute on function public.assign_incident_team(uuid, uuid) to authenticated;
