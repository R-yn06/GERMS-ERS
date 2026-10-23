-- GERMS 003: privileged operations as SECURITY DEFINER RPCs. Each checks the caller itself and writes audit rows atomically.

create or replace function private.audit(p_action text, p_entity text, p_entity_id text, p_details jsonb default null)
returns void language sql security definer set search_path = public as $$
  insert into audit_logs(actor_id, action, entity, entity_id, details) values (auth.uid(), p_action, p_entity, p_entity_id, p_details) $$;

create or replace function private.require_admin() returns void language plpgsql stable security definer set search_path = public as $$
begin if not public.is_admin() then raise exception 'Administrator role required' using errcode = '42501'; end if; end $$;

-- ---------- incidents ----------
create or replace function public.create_incident(p jsonb) returns uuid
language plpgsql security definer set search_path = public as $$
declare v_id uuid; r jsonb; v_div uuid; v_cls uuid;
begin
  perform private.require_admin();
  insert into incidents(title, description, category, severity, location_text, barangay, district, latitude, longitude,
                        hazards, access_restrictions, personnel_requested, equipment_notes, staging_location, instructions,
                        is_public, public_summary, created_by)
  values (p->>'title', p->>'description', coalesce(nullif(p->>'category','')::incident_category,'other'),
          coalesce(nullif(p->>'severity','')::incident_severity,'moderate'), p->>'location_text', p->>'barangay', p->>'district',
          nullif(p->>'latitude','')::numeric, nullif(p->>'longitude','')::numeric, p->>'hazards', p->>'access_restrictions',
          coalesce(nullif(p->>'personnel_requested','')::int,0), p->>'equipment_notes', p->>'staging_location', p->>'instructions',
          coalesce((p->>'is_public')::boolean,false), p->>'public_summary', auth.uid())
  returning id into v_id;

  for r in select * from jsonb_array_elements(coalesce(p->'requirements','[]'::jsonb)) loop
    select id into v_div from service_divisions where code = r->>'division_code';
    if v_div is null then raise exception 'Unknown division code: %', r->>'division_code'; end if;
    v_cls := null;
    if coalesce(r->>'classification_code','') <> '' then
      select id into v_cls from responder_classifications where code = r->>'classification_code';
    end if;
    insert into incident_requirements(incident_id, division_id, classification_id, headcount)
    values (v_id, v_div, v_cls, coalesce(nullif(r->>'headcount','')::int,1));
  end loop;

  insert into incident_updates(incident_id, author_id, kind, message, to_status) values (v_id, auth.uid(), 'status_change', 'Draft created', 'draft');
  perform private.audit('incident.create','incident', v_id::text, jsonb_build_object('title', p->>'title'));
  return v_id;
end $$;

create or replace function public.update_incident(p_id uuid, p jsonb) returns void
language plpgsql security definer set search_path = public as $$
declare v_status incident_status;
begin
  perform private.require_admin();
  select status into v_status from incidents where id = p_id for update;
  if v_status is null then raise exception 'Incident not found'; end if;
  if v_status in ('closed','archived') then raise exception 'Closed or archived incidents cannot be edited'; end if;
  update incidents set
    title = coalesce(p->>'title', title), description = coalesce(p->>'description', description),
    category = coalesce(nullif(p->>'category','')::incident_category, category),
    severity = coalesce(nullif(p->>'severity','')::incident_severity, severity),
    location_text = coalesce(p->>'location_text', location_text), barangay = coalesce(p->>'barangay', barangay),
    district = coalesce(p->>'district', district),
    latitude = coalesce(nullif(p->>'latitude','')::numeric, latitude), longitude = coalesce(nullif(p->>'longitude','')::numeric, longitude),
    hazards = coalesce(p->>'hazards', hazards), access_restrictions = coalesce(p->>'access_restrictions', access_restrictions),
    personnel_requested = coalesce(nullif(p->>'personnel_requested','')::int, personnel_requested),
    equipment_notes = coalesce(p->>'equipment_notes', equipment_notes), staging_location = coalesce(p->>'staging_location', staging_location),
    instructions = coalesce(p->>'instructions', instructions), is_public = coalesce((p->>'is_public')::boolean, is_public),
    public_summary = coalesce(p->>'public_summary', public_summary)
  where id = p_id;
  insert into incident_updates(incident_id, author_id, kind, message) values (p_id, auth.uid(), 'note', 'Incident details edited');
  perform private.audit('incident.update','incident', p_id::text, p);
end $$;

-- Publishes a draft and creates one notification per eligible responder. Returns the number of notifications created.
create or replace function public.publish_incident(p_id uuid) returns int
language plpgsql security definer set search_path = public as $$
declare v incidents%rowtype; v_count int;
begin
  perform private.require_admin();
  select * into v from incidents where id = p_id for update;
  if v.id is null then raise exception 'Incident not found'; end if;
  if not exists (select 1 from incident_status_transitions where from_status = v.status and to_status = 'published') then
    raise exception 'Cannot publish an incident in status %', v.status;
  end if;

  update incidents set status = 'published', published_by = auth.uid(), published_at = now() where id = p_id;
  insert into incident_updates(incident_id, author_id, kind, message, from_status, to_status)
    values (p_id, auth.uid(), 'status_change', 'Incident published', v.status, 'published');

  insert into notifications(recipient_id, incident_id, kind, priority, title, body)
  select pr.id, p_id, 'incident_published',
         case v.severity when 'critical' then 'critical' when 'high' then 'high' else 'normal' end,
         '[' || upper(v.severity::text) || '] ' || v.title,
         coalesce(v.location_text, v.barangay, v.city)
  from profiles pr
  left join responder_availability av on av.profile_id = pr.id
  where pr.role = 'responder' and pr.is_active
    and coalesce(av.status,'available') in ('available','standby')
    and (not exists (select 1 from incident_requirements r where r.incident_id = p_id)
         or exists (select 1 from incident_requirements r join profile_divisions d on d.division_id = r.division_id
                    where r.incident_id = p_id and d.profile_id = pr.id));
  get diagnostics v_count = row_count;

  perform private.audit('incident.publish','incident', p_id::text, jsonb_build_object('notified', v_count));
  return v_count;
end $$;

create or replace function public.set_incident_status(p_id uuid, p_to incident_status, p_note text default null) returns void
language plpgsql security definer set search_path = public as $$
declare v_from incident_status;
begin
  perform private.require_admin();
  if p_to = 'published' then raise exception 'Use publish_incident to publish'; end if;
  select status into v_from from incidents where id = p_id for update;
  if v_from is null then raise exception 'Incident not found'; end if;
  if not exists (select 1 from incident_status_transitions where from_status = v_from and to_status = p_to) then
    raise exception 'Transition % -> % is not allowed', v_from, p_to;
  end if;
  if p_to = 'closed' and coalesce(trim(p_note),'') = '' then raise exception 'A closure summary is required'; end if;

  update incidents set status = p_to,
    closure_summary = case when p_to = 'closed' then p_note else closure_summary end,
    closed_by = case when p_to = 'closed' then auth.uid() else closed_by end,
    closed_at = case when p_to = 'closed' then now() else closed_at end
  where id = p_id;
  insert into incident_updates(incident_id, author_id, kind, message, from_status, to_status)
    values (p_id, auth.uid(), 'status_change', p_note, v_from, p_to);
  perform private.audit('incident.status','incident', p_id::text, jsonb_build_object('from', v_from, 'to', p_to));
end $$;

create or replace function public.post_incident_update(p_incident uuid, p_message text) returns uuid
language plpgsql security definer set search_path = public as $$
declare v_id uuid; v_kind text;
begin
  if coalesce(trim(p_message),'') = '' then raise exception 'Message is required'; end if;
  if public.is_admin() then v_kind := 'note';
  elsif exists (select 1 from incident_assignments where incident_id = p_incident and responder_id = auth.uid()
                and status not in ('completed','released','cancelled')) then v_kind := 'field_update';
  else raise exception 'Only administrators or actively assigned responders can post updates' using errcode = '42501';
  end if;
  insert into incident_updates(incident_id, author_id, kind, message) values (p_incident, auth.uid(), v_kind, p_message) returning id into v_id;
  return v_id;
end $$;

-- ---------- responder decision ----------
create or replace function public.respond_to_incident(p_incident uuid, p_decision response_decision, p_note text default null) returns uuid
language plpgsql security definer set search_path = public as $$
declare v_id uuid; v_status incident_status;
begin
  if not public.is_responder() then raise exception 'Responder role required' using errcode = '42501'; end if;
  if not public.can_view_incident(p_incident) then raise exception 'Incident not available to you' using errcode = '42501'; end if;
  select status into v_status from incidents where id = p_incident;
  if v_status not in ('published','acknowledged','mobilizing','active_response') then
    raise exception 'Incident is not accepting responses (status: %)', v_status;
  end if;
  if p_decision = 'declined' and exists (select 1 from incident_assignments where incident_id = p_incident and responder_id = auth.uid()
                                         and status not in ('completed','released','cancelled')) then
    raise exception 'You hold an active assignment; ask command to release you instead';
  end if;
  insert into incident_responses(incident_id, responder_id, decision, note) values (p_incident, auth.uid(), p_decision, p_note)
  on conflict (incident_id, responder_id) do update set decision = excluded.decision, note = excluded.note, responded_at = now()
  returning id into v_id;
  perform private.audit('incident.respond','incident', p_incident::text, jsonb_build_object('decision', p_decision));
  return v_id;
end $$;

-- ---------- assignment ----------
create or replace function public.assign_responder(p_incident uuid, p_responder uuid, p_role text default 'Assigned Responder',
                                                   p_note text default null, p_override boolean default false) returns uuid
language plpgsql security definer set search_path = public as $$
declare v_status incident_status; v_title text; v_id uuid; v_one boolean; v_avail availability_status; v_decision response_decision;
begin
  perform private.require_admin();
  perform pg_advisory_xact_lock(hashtext(p_responder::text));  -- serialize concurrent assignments of the same person

  select status, title into v_status, v_title from incidents where id = p_incident for update;
  if v_status is null then raise exception 'Incident not found'; end if;
  if v_status in ('draft','closed','archived') then raise exception 'Cannot assign personnel to an incident in status %', v_status; end if;
  if not exists (select 1 from profiles where id = p_responder and role = 'responder' and is_active) then
    raise exception 'Target is not an active responder';
  end if;

  select status into v_avail from responder_availability where profile_id = p_responder;
  if coalesce(v_avail,'available') in ('off_duty','unavailable') and not p_override then
    raise exception 'Responder is % (override required)', coalesce(v_avail,'available');
  end if;
  select decision into v_decision from incident_responses where incident_id = p_incident and responder_id = p_responder;
  if v_decision = 'declined' and not p_override then raise exception 'Responder declined this incident (override required)'; end if;

  select coalesce((value)::boolean, true) into v_one from system_settings where key = 'one_active_assignment';
  if coalesce(v_one, true) and exists (select 1 from incident_assignments
        where responder_id = p_responder and incident_id <> p_incident and status not in ('completed','released','cancelled')) then
    raise exception 'Responder already holds an active assignment on another incident';
  end if;

  begin
    insert into incident_assignments(incident_id, responder_id, role_name, note, assigned_by)
      values (p_incident, p_responder, coalesce(nullif(trim(p_role),''),'Assigned Responder'), p_note, auth.uid()) returning id into v_id;
  exception when unique_violation then
    raise exception 'Responder is already actively assigned to this incident';
  end;

  insert into assignment_status_history(assignment_id, from_status, to_status, changed_by, note) values (v_id, null, 'assigned', auth.uid(), p_note);
  insert into notifications(recipient_id, incident_id, kind, priority, title, body)
    values (p_responder, p_incident, 'assignment', 'high', 'Official assignment: ' || v_title, 'Role: ' || coalesce(nullif(trim(p_role),''),'Assigned Responder'));
  perform private.audit('assignment.create','incident_assignment', v_id::text,
                        jsonb_build_object('incident', p_incident, 'responder', p_responder, 'override', p_override));
  return v_id;
end $$;

create or replace function public.update_assignment_status(p_assignment uuid, p_to assignment_status, p_note text default null) returns void
language plpgsql security definer set search_path = public as $$
declare a incident_assignments%rowtype; v_admin boolean := public.is_admin(); v_ok boolean;
begin
  select * into a from incident_assignments where id = p_assignment for update;
  if a.id is null then raise exception 'Assignment not found'; end if;
  if not v_admin and a.responder_id <> auth.uid() then raise exception 'Not your assignment' using errcode = '42501'; end if;
  if a.status in ('completed','released','cancelled') and not (a.status = 'completed' and p_to = 'released' and v_admin) then
    raise exception 'Assignment is already %', a.status;
  end if;

  select exists (select 1 from assignment_status_transitions t where t.from_status = a.status and t.to_status = p_to
                 and (v_admin or t.responder_allowed)) into v_ok;
  if not v_ok and v_admin and p_to in ('released','cancelled') then v_ok := true; end if;  -- admins may release/cancel from any active state
  if not v_ok then raise exception 'Transition % -> % is not allowed for your role', a.status, p_to; end if;

  update incident_assignments set status = p_to,
         released_at = case when p_to in ('released','cancelled') then now() else released_at end where id = p_assignment;
  insert into assignment_status_history(assignment_id, from_status, to_status, changed_by, note) values (p_assignment, a.status, p_to, auth.uid(), p_note);
  perform private.audit('assignment.status','incident_assignment', p_assignment::text, jsonb_build_object('from', a.status, 'to', p_to));
end $$;

-- ---------- notification receipts ----------
create or replace function public.mark_notification_observed(p_id uuid) returns void
language plpgsql security definer set search_path = public as $$
begin
  if not exists (select 1 from notifications where id = p_id and recipient_id = auth.uid()) then
    raise exception 'Notification not found' using errcode = '42501'; end if;
  insert into notification_receipts(notification_id, observed_at) values (p_id, now())
    on conflict (notification_id) do update set observed_at = coalesce(notification_receipts.observed_at, now());
end $$;

create or replace function public.acknowledge_notification(p_id uuid) returns void
language plpgsql security definer set search_path = public as $$
begin
  if not exists (select 1 from notifications where id = p_id and recipient_id = auth.uid()) then
    raise exception 'Notification not found' using errcode = '42501'; end if;
  insert into notification_receipts(notification_id, observed_at, acknowledged_at) values (p_id, now(), now())
    on conflict (notification_id) do update set observed_at = coalesce(notification_receipts.observed_at, now()),
                                               acknowledged_at = coalesce(notification_receipts.acknowledged_at, now());
end $$;

-- ---------- admin dashboard summary ----------
create or replace function public.incident_response_summary(p_incident uuid) returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare v_requested int; v_notified int; v_acc int; v_dec int; v_asg int;
begin
  perform private.require_admin();
  select personnel_requested into v_requested from incidents where id = p_incident;
  select count(*) into v_notified from notifications where incident_id = p_incident and kind = 'incident_published';
  select count(*) filter (where decision = 'accepted'), count(*) filter (where decision = 'declined') into v_acc, v_dec
    from incident_responses where incident_id = p_incident;
  select count(*) into v_asg from incident_assignments where incident_id = p_incident and status not in ('released','cancelled');
  return jsonb_build_object('requested', v_requested, 'notified', v_notified, 'accepted', v_acc, 'declined', v_dec,
                            'pending', greatest(v_notified - v_acc - v_dec, 0), 'assigned', v_asg,
                            'remaining', greatest(coalesce(v_requested,0) - v_asg, 0));
end $$;

-- ---------- execute privileges: authenticated only ----------
revoke execute on all functions in schema public from public, anon;
grant execute on all functions in schema public to authenticated;

-- ---------- Realtime (RLS applies to postgres_changes) ----------
do $$ declare t text; begin
  foreach t in array array['incidents','incident_responses','incident_assignments','incident_updates','notifications','assignment_status_history'] loop
    begin execute format('alter publication supabase_realtime add table public.%I', t);
    exception when duplicate_object then null; when undefined_object then raise notice 'supabase_realtime publication not found'; end;
  end loop; end $$;
