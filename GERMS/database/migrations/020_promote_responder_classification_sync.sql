-- GERMS 020: keep the promoted responder's selected classification authoritative.

CREATE OR REPLACE FUNCTION public.promote_to_responder(
  p_profile uuid,
  p_classification_code text,
  p_division_code text DEFAULT NULL
) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE
  v_div uuid;
  v_cls uuid;
  v_code text := upper(trim(coalesce(p_classification_code, '')));
  v_division_code text;
BEGIN
  PERFORM private.require_admin();

  IF v_code NOT IN ('EMS', 'ERU', 'EMR', 'EMT', 'DMS') THEN
    RAISE EXCEPTION 'Unsupported responder classification';
  END IF;

  SELECT rc.id, rc.division_id, d.code
    INTO v_cls, v_div, v_division_code
    FROM responder_classifications rc
    JOIN service_divisions d ON d.id = rc.division_id
    WHERE rc.code = v_code;

  IF v_cls IS NULL THEN
    RAISE EXCEPTION 'Responder classification not found';
  END IF;

  IF coalesce(nullif(trim(p_division_code), ''), v_division_code) <> v_division_code THEN
    RAISE EXCEPTION 'Classification does not belong to the selected division';
  END IF;

  UPDATE profiles SET role = 'responder', is_active = true WHERE id = p_profile;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Target profile not found';
  END IF;

  DELETE FROM profile_classifications WHERE profile_id = p_profile;
  INSERT INTO profile_classifications (profile_id, classification_id, verified_by, verified_at)
    VALUES (p_profile, v_cls, auth.uid(), now());

  INSERT INTO profile_divisions (profile_id, division_id)
    VALUES (p_profile, v_div)
    ON CONFLICT DO NOTHING;

  INSERT INTO responder_availability (profile_id, status, note, updated_at)
    VALUES (p_profile, 'available', 'Promoted to responder by command staff', now())
    ON CONFLICT (profile_id) DO UPDATE SET status = 'available', updated_at = now();

  PERFORM private.audit('profile.promote_responder', 'profile', p_profile::text,
    jsonb_build_object('role', 'responder', 'classification', v_code, 'division', v_division_code));
END $$;
