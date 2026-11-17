-- GERMS 014: allow administrators to acknowledge shared responder alerts.

create table admin_notification_receipts (
  notification_id uuid primary key references notifications(id) on delete cascade,
  acknowledged_by uuid not null references profiles(id),
  acknowledged_at timestamptz not null default now()
);

alter table admin_notification_receipts enable row level security;
grant select on admin_notification_receipts to authenticated;
create policy admin_notification_receipts_select on admin_notification_receipts
  for select to authenticated using (public.is_admin());

create or replace function public.admin_acknowledge_notification(p_id uuid)
returns void
language plpgsql security definer set search_path = public as $$
begin
  perform private.require_admin();

  if not exists (select 1 from notifications where id = p_id) then
    raise exception 'Notification not found';
  end if;

  insert into admin_notification_receipts(notification_id, acknowledged_by, acknowledged_at)
  values (p_id, auth.uid(), now())
  on conflict (notification_id) do update
    set acknowledged_by = auth.uid(), acknowledged_at = now();

  perform private.audit('notification.admin_acknowledge', 'notification', p_id::text, null);
end $$;

grant execute on function public.admin_acknowledge_notification(uuid) to authenticated;
