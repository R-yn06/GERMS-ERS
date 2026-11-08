-- GERMS 010: Fix Auth signup trigger type resolution under Supabase's auth search_path.
-- Safe to apply after migration 009 on existing projects.

CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS trigger AS $$
DECLARE
  v_name text;
  v_role public.user_role;
BEGIN
  v_name := COALESCE(
    NULLIF(TRIM(NEW.raw_user_meta_data->>'full_name'), ''),
    split_part(COALESCE(NEW.email, 'user'), '@', 1)
  );
  IF v_name IS NULL OR length(trim(v_name)) = 0 THEN
    v_name := 'User ' || substr(NEW.id::text, 1, 8);
  END IF;

  BEGIN
    v_role := 'guest'::public.user_role;
  EXCEPTION WHEN OTHERS THEN
    v_role := 'responder'::public.user_role;
  END;

  INSERT INTO public.profiles AS current_profile (id, full_name, email, phone, role, is_active)
  VALUES (
    NEW.id,
    v_name,
    NEW.email,
    NULLIF(TRIM(NEW.raw_user_meta_data->>'phone'), ''),
    v_role,
    true
  )
  ON CONFLICT (id) DO UPDATE SET
    full_name = CASE
      WHEN current_profile.full_name IS NULL OR current_profile.full_name = ''
        THEN EXCLUDED.full_name
      ELSE current_profile.full_name
    END,
    email = COALESCE(EXCLUDED.email, current_profile.email);

  RETURN NEW;
EXCEPTION WHEN OTHERS THEN
  RAISE WARNING 'handle_new_user failed for auth.user %: %', NEW.id, SQLERRM;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = '';
