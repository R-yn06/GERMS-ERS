-- GERMS 018: track public reports securely, review them as drafts, and expose public directories.

alter table public.guest_reports
  add column title text,
  add column category public.incident_category not null default 'other',
  add column severity public.incident_severity not null default 'moderate',
  add column reporter_id uuid references public.profiles(id) on delete set null,
  add column tracking_token_hash bytea,
  add column converted_incident_id uuid references public.incidents(id) on delete set null;

update public.guest_reports
set title = coalesce(nullif(trim(split_part(description, E'\n', 1)), ''), 'Public incident report')
where title is null;

alter table public.guest_reports alter column title set not null;
create index guest_reports_reporter_id_idx on public.guest_reports(reporter_id, created_at desc);
create index guest_reports_tracking_token_hash_idx on public.guest_reports(tracking_token_hash);

grant select on public.guest_reports to authenticated;
create policy guest_report_owner_select on public.guest_reports
  for select to authenticated using (reporter_id = auth.uid());

create or replace function public.submit_guest_report(p_report jsonb, p_tracking_token text default null)
returns uuid
language plpgsql
security definer
set search_path = public, extensions, pg_temp
as $$
declare
  v_id uuid;
  v_title text := trim(coalesce(p_report->>'title', ''));
  v_description text := coalesce(p_report->>'description', '');
begin
  if auth.uid() is null and length(coalesce(p_tracking_token, '')) < 32 then
    raise exception 'A valid report tracking token is required' using errcode = '22023';
  end if;
  if v_title = '' then raise exception 'Report title is required' using errcode = '22023'; end if;
  if length(v_description) < 10 or length(v_description) > 2000 then
    raise exception 'Report description must be between 10 and 2,000 characters' using errcode = '22023';
  end if;
  if length(coalesce(p_report->>'location_text', '')) > 300 then
    raise exception 'Location must be no longer than 300 characters' using errcode = '22023';
  end if;
  if length(coalesce(p_report->>'reporter_name', '')) > 120 then
    raise exception 'Reporter name must be no longer than 120 characters' using errcode = '22023';
  end if;

  insert into public.guest_reports (
    id, reporter_name, title, category, severity, description, location_text, latitude, longitude,
    status, reporter_id, tracking_token_hash
  ) values (
    coalesce(nullif(p_report->>'id', '')::uuid, gen_random_uuid()),
    nullif(p_report->>'reporter_name', ''),
    v_title,
    coalesce(nullif(p_report->>'category', '')::incident_category, 'other'),
    coalesce(nullif(p_report->>'severity', '')::incident_severity, 'moderate'),
    v_description,
    nullif(p_report->>'location_text', ''),
    nullif(p_report->>'latitude', '')::numeric,
    nullif(p_report->>'longitude', '')::numeric,
    'unverified',
    auth.uid(),
    case when auth.uid() is null then digest(p_tracking_token, 'sha256') else null end
  ) returning id into v_id;

  return v_id;
end;
$$;

create or replace function public.my_guest_reports(p_tracking_token text default null)
returns table (
  id uuid,
  title text,
  description text,
  location_text text,
  status text,
  created_at timestamptz,
  converted_incident_id uuid
)
language plpgsql
stable
security definer
set search_path = public, extensions, pg_temp
as $$
begin
  if auth.uid() is null and length(coalesce(p_tracking_token, '')) < 32 then
    raise exception 'A valid report tracking token is required' using errcode = '22023';
  end if;

  return query
  select r.id, r.title, r.description, r.location_text, r.status, r.created_at, r.converted_incident_id
  from public.guest_reports r
  where (auth.uid() is not null and r.reporter_id = auth.uid())
     or (p_tracking_token is not null and r.tracking_token_hash = digest(p_tracking_token, 'sha256'))
  order by r.created_at desc;
end;
$$;

create or replace function public.review_guest_report(p_report uuid, p_approve boolean)
returns uuid
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_report public.guest_reports%rowtype;
  v_incident uuid;
begin
  perform private.require_admin();
  select * into v_report
  from public.guest_reports
  where id = p_report
  for update;

  if v_report.id is null then raise exception 'Report not found'; end if;
  if v_report.status <> 'unverified' then raise exception 'Report has already been reviewed'; end if;

  if p_approve then
    insert into public.incidents (
      title, description, category, severity, status, reported_at, location_text, latitude, longitude,
      is_public, public_summary, created_by
    ) values (
      v_report.title, v_report.description, v_report.category, v_report.severity, 'draft', v_report.created_at,
      v_report.location_text, v_report.latitude, v_report.longitude, true, v_report.description, auth.uid()
    ) returning id into v_incident;

    insert into public.incident_updates (incident_id, author_id, kind, message, to_status)
    values (v_incident, auth.uid(), 'status_change', 'Public report approved and converted to an incident draft', 'draft');

    update public.guest_reports
    set status = 'converted', reviewed_by = auth.uid(), reviewed_at = now(), converted_incident_id = v_incident
    where id = p_report;

    perform private.audit('guest_report.approve', 'guest_report', p_report::text,
      jsonb_build_object('incident_id', v_incident));
    return v_incident;
  end if;

  update public.guest_reports
  set status = 'rejected', reviewed_by = auth.uid(), reviewed_at = now()
  where id = p_report;
  perform private.audit('guest_report.reject', 'guest_report', p_report::text, null);
  return null;
end;
$$;

revoke all on function public.submit_guest_report(jsonb, text) from public;
revoke all on function public.my_guest_reports(text) from public;
revoke all on function public.review_guest_report(uuid, boolean) from public, anon;
grant execute on function public.submit_guest_report(jsonb, text) to anon, authenticated;
grant execute on function public.my_guest_reports(text) to anon, authenticated;
grant execute on function public.review_guest_report(uuid, boolean) to authenticated;

grant select (id, name, address, capacity, occupancy, status, latitude, longitude)
  on public.evacuation_centers to anon;
create policy evacuation_centers_public_read on public.evacuation_centers
  for select to anon using (status <> 'closed');

notify pgrst, 'reload schema';
