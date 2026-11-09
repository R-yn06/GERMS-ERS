-- GERMS 007: administrator-controlled profile and responder management.
-- Account creation/deletion remains an Auth Admin operation in the Edge Function.

create or replace function public.update_profile_admin(
  p_profile uuid, p_full_name text, p_email text, p_phone text, p_role user_role, p_active boolean
) returns void
language plpgsql security definer set search_path = public as $$
begin
  perform private.require_admin();
  if p_profile = auth.uid() and (not p_active or p_role <> 'admin') then
    raise exception 'You cannot deactivate or demote your own administrator account';
  end if;
  if coalesce(trim(p_full_name),'') = '' then raise exception 'Full name is required'; end if;
  update profiles set full_name = trim(p_full_name), email = nullif(trim(p_email),''),
    phone = nullif(trim(p_phone),''), role = p_role, is_active = p_active
    where id = p_profile;
  if not found then raise exception 'Profile not found'; end if;
  perform private.audit('profile.update','profile',p_profile::text,
    jsonb_build_object('role',p_role,'active',p_active));
end $$;

create or replace function public.deactivate_profile_admin(p_profile uuid) returns void
language plpgsql security definer set search_path = public as $$
begin
  perform public.update_profile_admin(p_profile, (select full_name from profiles where id=p_profile),
    (select email from profiles where id=p_profile), (select phone from profiles where id=p_profile),
    (select role from profiles where id=p_profile), false);
end $$;

grant execute on function public.update_profile_admin(uuid,text,text,text,user_role,boolean) to authenticated;
grant execute on function public.deactivate_profile_admin(uuid) to authenticated;
