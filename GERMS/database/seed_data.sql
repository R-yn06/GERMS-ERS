-- GERMS sample data. SYNTHETIC / DEMONSTRATION ONLY. Run AFTER 001-004 and after creating your first admin.
--
-- Bootstrap the first administrator (once):
--   1. Supabase Dashboard > Authentication > Users > Add user (email + password).
--   2. Run, replacing the email and name:
--        insert into profiles(id, full_name, email, role)
--        select id, 'HQ Administrator', email, 'admin' from auth.users where email = 'admin@example.org';
--   Further accounts are created through the admin-create-user Edge Function (planned, step 3 of the build order).

do $$
declare v_admin uuid;
begin
  select id into v_admin from profiles where role = 'admin' limit 1;
  if v_admin is null then raise notice 'No admin profile yet; skipping sample incident.'; return; end if;
  insert into incidents(title, description, category, severity, location_text, barangay, district, personnel_requested, instructions, created_by)
  values ('[SAMPLE] Flooding along a riverside barangay', 'Synthetic demonstration incident. Not a real event.',
          'flood', 'high', 'Sample riverside area', 'Sample Barangay', 'Sample District', 10, 'Demonstration only.', v_admin);
end $$;
