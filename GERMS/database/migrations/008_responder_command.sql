-- GERMS 008: administrator controls for responder readiness and deployment requests.

create or replace function public.admin_set_responder_availability(
  p_profile uuid, p_status availability_status, p_note text default null
) returns void
language plpgsql security definer set search_path = public as $$
begin
  perform private.require_admin();
  if not exists (select 1 from profiles where id = p_profile and role = 'responder') then
    raise exception 'Active responder profile not found';
  end if;
  insert into responder_availability(profile_id, status, note, updated_at)
    values (p_profile, p_status, p_note, now())
  on conflict (profile_id) do update set status = excluded.status, note = excluded.note, updated_at = now();
  perform private.audit('responder.availability','profile',p_profile::text,
    jsonb_build_object('status',p_status,'note',p_note));
end $$;

create or replace function public.request_responder_deployment(
  p_incident uuid, p_responder uuid, p_instructions text default null
) returns uuid
language plpgsql security definer set search_path = public as $$
declare v_notification uuid; v_title text; v_location text; v_status incident_status; v_availability availability_status;
begin
  perform private.require_admin();
  select title, location_text, status into v_title, v_location, v_status
    from incidents where id = p_incident for update;
  if v_status is null then raise exception 'Incident not found'; end if;
  if v_status in ('draft','closed','archived') then raise exception 'Incident is not accepting deployment requests'; end if;
  if not exists (select 1 from profiles where id = p_responder and role = 'responder' and is_active) then
    raise exception 'Target is not an active responder';
  end if;
  select status into v_availability from responder_availability where profile_id = p_responder;
  if coalesce(v_availability,'available') in ('off_duty','unavailable') then
    raise exception 'Responder is not available for a deployment request';
  end if;
  if exists (select 1 from incident_assignments where responder_id = p_responder
             and status not in ('completed','released','cancelled')) then
    raise exception 'Responder is already deployed on an active assignment';
  end if;
  if exists (select 1 from notifications where recipient_id = p_responder and incident_id = p_incident
             and kind = 'deployment_request' and created_at > now() - interval '24 hours') then
    raise exception 'A deployment request was already sent to this responder';
  end if;
  insert into notifications(recipient_id, incident_id, kind, priority, title, body)
    values (p_responder, p_incident, 'deployment_request',
      (select case severity when 'critical' then 'critical' when 'high' then 'high' else 'normal' end from incidents where id=p_incident),
      'Deployment request: ' || v_title,
      coalesce(v_location,'Location not provided') || E'\n' || coalesce(p_instructions,'Please review and respond in GERMS.'))
    returning id into v_notification;
  perform private.audit('deployment.request','incident',p_incident::text,
    jsonb_build_object('responder',p_responder));
  return v_notification;
end $$;

grant execute on function public.admin_set_responder_availability(uuid,availability_status,text) to authenticated;
grant execute on function public.request_responder_deployment(uuid,uuid,text) to authenticated;
