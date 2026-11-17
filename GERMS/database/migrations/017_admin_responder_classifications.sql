-- GERMS 017: Manage responder classifications from admin accounts and show them in incident chat.

insert into public.responder_classifications (division_id, code, name, requires_certification)
select d.id, c.code, c.name, c.requires_certification
from public.service_divisions d
join (values
  ('SAFETY', 'EMS', 'Emergency Medical Services', true),
  ('SAFETY', 'ERU', 'Emergency Response Unit', true),
  ('SAFETY', 'EMR', 'Emergency Medical Responder', true),
  ('SAFETY', 'EMT', 'Emergency Medical Technician', true),
  ('DMS', 'DMS', 'Disaster Management Services', false)
) as c(division_code, code, name, requires_certification) on c.division_code = d.code
on conflict (code) do nothing;

create or replace function public.set_own_responder_classification(p_classification_code text)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_classification_id uuid;
  v_division_id uuid;
  v_code text := upper(trim(coalesce(p_classification_code, '')));
begin
  if not public.is_responder() then
    raise exception 'Active responder role required' using errcode = '42501';
  end if;

  if v_code not in ('EMS', 'ERU', 'EMR', 'EMT', 'DMS') then
    raise exception 'Unsupported responder classification';
  end if;

  select id, division_id
  into v_classification_id, v_division_id
  from public.responder_classifications
  where code = v_code;

  if v_classification_id is null then
    raise exception 'Responder classification not found';
  end if;

  delete from public.profile_classifications
  where profile_id = auth.uid();

  insert into public.profile_classifications (profile_id, classification_id)
  values (auth.uid(), v_classification_id);

  insert into public.profile_divisions (profile_id, division_id)
  values (auth.uid(), v_division_id)
  on conflict do nothing;

  perform private.audit(
    'profile.self_classification',
    'profile',
    auth.uid()::text,
    jsonb_build_object('classification', v_code)
  );
end;
$$;

revoke all on function public.set_own_responder_classification(text) from public, anon;
grant execute on function public.set_own_responder_classification(text) to authenticated;

create or replace function public.update_profile_admin_with_classification(
  p_profile uuid,
  p_full_name text,
  p_email text,
  p_phone text,
  p_role public.user_role,
  p_active boolean,
  p_classification_code text
) returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_classification_id uuid;
  v_division_id uuid;
  v_code text := upper(trim(coalesce(p_classification_code, '')));
begin
  perform private.require_admin();
  perform public.update_profile_admin(
    p_profile, p_full_name, p_email, p_phone, p_role, p_active
  );

  if p_role <> 'responder' then
    delete from public.profile_classifications
    where profile_id = p_profile;
    return;
  end if;

  if v_code = '' then
    delete from public.profile_classifications
    where profile_id = p_profile;
    perform private.audit(
      'profile.classification_admin',
      'profile',
      p_profile::text,
      jsonb_build_object('classification', null)
    );
    return;
  end if;

  select id, division_id
  into v_classification_id, v_division_id
  from public.responder_classifications
  where code = v_code;

  if v_classification_id is null then
    raise exception 'Responder classification not found';
  end if;

  delete from public.profile_classifications
  where profile_id = p_profile;

  insert into public.profile_classifications
    (profile_id, classification_id, verified_by, verified_at)
  values
    (p_profile, v_classification_id, auth.uid(), now());

  insert into public.profile_divisions (profile_id, division_id)
  values (p_profile, v_division_id)
  on conflict do nothing;

  perform private.audit(
    'profile.classification_admin',
    'profile',
    p_profile::text,
    jsonb_build_object('classification', v_code)
  );
end;
$$;

revoke all on function public.update_profile_admin_with_classification(
  uuid, text, text, text, public.user_role, boolean, text
) from public, anon;
grant execute on function public.update_profile_admin_with_classification(
  uuid, text, text, text, public.user_role, boolean, text
) to authenticated;

create or replace function public.incident_message_sender_directory(p_incident uuid)
returns table (
  sender_id uuid,
  full_name text,
  sender_role text,
  classification_code text
)
language plpgsql
stable
security definer
set search_path = public, pg_temp
as $$
begin
  if auth.uid() is null or not public.can_view_incident(p_incident) then
    raise exception 'Incident not available to you' using errcode = '42501';
  end if;

  return query
  select p.id,
         p.full_name,
         p.role::text,
         classification.code
  from public.profiles p
  join (
    select distinct m.sender_id
    from public.incident_messages m
    where m.incident_id = p_incident
  ) senders on senders.sender_id = p.id
  left join lateral (
    select rc.code
    from public.profile_classifications pc
    join public.responder_classifications rc on rc.id = pc.classification_id
    where pc.profile_id = p.id
    order by rc.code
    limit 1
  ) classification on true;
end;
$$;

revoke all on function public.incident_message_sender_directory(uuid) from public, anon;
grant execute on function public.incident_message_sender_directory(uuid) to authenticated;

notify pgrst, 'reload schema';
