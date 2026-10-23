-- GERMS 002: Row Level Security. Default-deny, then explicit grants.
revoke all on all tables in schema public from anon, authenticated;
revoke all on all sequences in schema public from anon, authenticated;

-- ---------- helpers (SECURITY DEFINER so policies cannot recurse) ----------
create or replace function public.is_admin() returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from profiles where id = auth.uid() and role = 'admin' and is_active) $$;
create or replace function public.is_responder() returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from profiles where id = auth.uid() and role = 'responder' and is_active) $$;

create or replace function public.can_view_incident(p_incident uuid) returns boolean language sql stable security definer set search_path = public as $$
  select public.is_admin()
  or exists (select 1 from incident_assignments a where a.incident_id = p_incident and a.responder_id = auth.uid())
  or exists (
    select 1 from incidents i
    where i.id = p_incident and i.status not in ('draft','archived') and public.is_responder()
      and (not exists (select 1 from incident_requirements r where r.incident_id = i.id)
           or exists (select 1 from incident_requirements r
                      join profile_divisions d on d.division_id = r.division_id and d.profile_id = auth.uid()
                      where r.incident_id = i.id)));
$$;

-- ---------- enable RLS everywhere ----------
do $$ declare t text; begin
  for t in select tablename from pg_tables where schemaname = 'public' loop
    execute format('alter table public.%I enable row level security', t);
  end loop; end $$;

-- ---------- profiles ----------
grant select on profiles to authenticated;
grant update (full_name, phone, avatar_path) on profiles to authenticated;  -- role/is_active NOT updatable by clients
create policy profiles_select on profiles for select to authenticated using (id = auth.uid() or public.is_admin());
create policy profiles_update_self on profiles for update to authenticated using (id = auth.uid()) with check (id = auth.uid());

-- ---------- reference data (read-only to clients) ----------
grant select on service_divisions, responder_classifications, certifications, incident_command_positions,
                incident_status_transitions, assignment_status_transitions, system_settings to authenticated;
create policy ref_select on service_divisions for select to authenticated using (true);
create policy ref_select on responder_classifications for select to authenticated using (true);
create policy ref_select on certifications for select to authenticated using (true);
create policy ref_select on incident_command_positions for select to authenticated using (true);
create policy ref_select on incident_status_transitions for select to authenticated using (true);
create policy ref_select on assignment_status_transitions for select to authenticated using (true);
create policy settings_select on system_settings for select to authenticated using (true);
grant update on system_settings to authenticated;
create policy settings_admin_update on system_settings for update to authenticated using (public.is_admin()) with check (public.is_admin());

-- ---------- personnel records: own read, admin full ----------
grant select, insert, update, delete on profile_divisions, profile_classifications, responder_qualifications to authenticated;
create policy own_or_admin_select on profile_divisions for select to authenticated using (profile_id = auth.uid() or public.is_admin());
create policy admin_write on profile_divisions for all to authenticated using (public.is_admin()) with check (public.is_admin());
create policy own_or_admin_select on profile_classifications for select to authenticated using (profile_id = auth.uid() or public.is_admin());
create policy admin_write on profile_classifications for all to authenticated using (public.is_admin()) with check (public.is_admin());
create policy own_or_admin_select on responder_qualifications for select to authenticated using (profile_id = auth.uid() or public.is_admin());
create policy admin_write on responder_qualifications for all to authenticated using (public.is_admin()) with check (public.is_admin());

grant select, insert, update on responder_availability, responder_locations to authenticated;
create policy own_or_admin_select on responder_availability for select to authenticated using (profile_id = auth.uid() or public.is_admin());
create policy own_insert on responder_availability for insert to authenticated with check (profile_id = auth.uid() and public.is_responder());
create policy own_update on responder_availability for update to authenticated using (profile_id = auth.uid()) with check (profile_id = auth.uid());
create policy own_or_admin_select on responder_locations for select to authenticated using (profile_id = auth.uid() or public.is_admin());
create policy own_insert on responder_locations for insert to authenticated with check (profile_id = auth.uid() and public.is_responder());
create policy own_update on responder_locations for update to authenticated using (profile_id = auth.uid()) with check (profile_id = auth.uid());

-- ---------- incidents: read-only for clients; every write goes through RPC ----------
grant select on incidents, incident_requirements, incident_updates, incident_command_assignments to authenticated;
create policy incidents_select on incidents for select to authenticated using (public.can_view_incident(id));
create policy req_select on incident_requirements for select to authenticated using (public.can_view_incident(incident_id));
create policy upd_select on incident_updates for select to authenticated using (public.can_view_incident(incident_id));
create policy cmd_select on incident_command_assignments for select to authenticated using (public.can_view_incident(incident_id));

grant select on incident_responses, incident_assignments, assignment_status_history to authenticated;
create policy resp_select on incident_responses for select to authenticated using (responder_id = auth.uid() or public.is_admin());
create policy asg_select on incident_assignments for select to authenticated using (responder_id = auth.uid() or public.is_admin());
create policy hist_select on assignment_status_history for select to authenticated using (
  public.is_admin() or exists (select 1 from incident_assignments a where a.id = assignment_id and a.responder_id = auth.uid()));

-- ---------- notifications ----------
grant select on notifications, notification_receipts to authenticated;
create policy notif_select on notifications for select to authenticated using (recipient_id = auth.uid() or public.is_admin());
create policy receipt_select on notification_receipts for select to authenticated using (
  public.is_admin() or exists (select 1 from notifications n where n.id = notification_id and n.recipient_id = auth.uid()));

-- ---------- audit: admin read, nobody writes directly ----------
grant select on audit_logs to authenticated;
create policy audit_select on audit_logs for select to authenticated using (public.is_admin());

-- ---------- guests ----------
grant insert on guest_reports to anon, authenticated;
grant select, update on guest_reports to authenticated;
create policy guest_insert on guest_reports for insert to anon, authenticated with check (status = 'unverified' and reviewed_by is null);
create policy guest_admin_select on guest_reports for select to authenticated using (public.is_admin());
create policy guest_admin_update on guest_reports for update to authenticated using (public.is_admin()) with check (public.is_admin());

-- Public advisories: a narrow view; only is_public incidents, no internal fields.
create or replace view public_advisories as
  select id, incident_no, title, category, severity, status, barangay, district, city, public_summary, published_at
  from incidents
  where is_public and status in ('published','acknowledged','mobilizing','active_response','stabilized');
grant select on public_advisories to anon, authenticated;
