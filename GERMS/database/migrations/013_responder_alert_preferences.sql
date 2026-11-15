-- GERMS 013: responder-controlled deployment alerts and broadcast dispatch requests.

create table responder_notification_preferences (
  profile_id uuid primary key references profiles(id) on delete cascade,
  deployment_notifications_enabled boolean not null default true,
  updated_at timestamptz not null default now()
);

alter table responder_notification_preferences enable row level security;
grant select, insert, update on responder_notification_preferences to authenticated;
create policy responder_notification_preferences_select on responder_notification_preferences
  for select to authenticated using (profile_id = auth.uid() or public.is_admin());
create policy responder_notification_preferences_update on responder_notification_preferences
  for insert to authenticated with check (profile_id = auth.uid());
create policy responder_notification_preferences_change on responder_notification_preferences
  for update to authenticated using (profile_id = auth.uid()) with check (profile_id = auth.uid());

create or replace function public.set_deployment_notification_preference(p_enabled boolean)
returns void
language plpgsql security definer set search_path = public as $$
begin
  if not public.is_responder() then raise exception 'Responder role required' using errcode = '42501'; end if;
  insert into responder_notification_preferences(profile_id, deployment_notifications_enabled, updated_at)
    values (auth.uid(), p_enabled, now())
  on conflict (profile_id) do update set deployment_notifications_enabled = excluded.deployment_notifications_enabled,
                                           updated_at = now();
end $$;

create or replace function public.request_all_active_responder_deployments(
  p_incident uuid, p_instructions text default null
) returns int
language plpgsql security definer set search_path = public as $$
declare v_incident incidents%rowtype; v_count int;
begin
  perform private.require_admin();
  select * into v_incident from incidents where id = p_incident for update;
  if v_incident.id is null then raise exception 'Incident not found'; end if;
  if v_incident.status in ('draft','closed','archived') then
    raise exception 'Incident is not accepting deployment requests';
  end if;

  insert into notifications(recipient_id, incident_id, kind, priority, title, body)
  select pr.id, p_incident, 'deployment_request',
         case v_incident.severity when 'critical' then 'critical' when 'high' then 'high' else 'normal' end,
         'Deployment request: ' || v_incident.title,
         coalesce(v_incident.location_text,'Location not provided') || E'\n' ||
           coalesce(p_instructions,'Please review and respond in GERMS.')
  from profiles pr
  left join responder_availability av on av.profile_id = pr.id
  left join responder_notification_preferences pref on pref.profile_id = pr.id
  where pr.role = 'responder' and pr.is_active
    and coalesce(av.status, 'available') in ('available','standby')
    and coalesce(pref.deployment_notifications_enabled, true)
    and not exists (
      select 1 from notifications n
      where n.recipient_id = pr.id and n.incident_id = p_incident
        and n.kind = 'deployment_request' and n.created_at > now() - interval '24 hours'
    );
  get diagnostics v_count = row_count;
  perform private.audit('deployment.request_all','incident',p_incident::text,
    jsonb_build_object('notified',v_count));
  return v_count;
end $$;

grant execute on function public.set_deployment_notification_preference(boolean) to authenticated;
grant execute on function public.request_all_active_responder_deployments(uuid,text) to authenticated;
