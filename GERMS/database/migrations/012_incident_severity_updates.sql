-- GERMS 012: Allow administrators and actively assigned responders to update incident severity.

CREATE OR REPLACE FUNCTION public.update_incident_severity(
  p_incident uuid,
  p_severity incident_severity
) RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_previous incident_severity;
BEGIN
  IF NOT public.is_admin() AND NOT (
    public.is_responder()
    AND EXISTS (
      SELECT 1
      FROM incident_assignments a
      WHERE a.incident_id = p_incident
        AND a.responder_id = auth.uid()
        AND a.status NOT IN ('completed', 'released', 'cancelled')
    )
  ) THEN
    RAISE EXCEPTION 'Only administrators or responders with an active assignment may update incident severity'
      USING errcode = '42501';
  END IF;

  SELECT severity
  INTO v_previous
  FROM incidents
  WHERE id = p_incident
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Incident not found';
  END IF;

  IF v_previous = p_severity THEN
    RETURN;
  END IF;

  UPDATE incidents
  SET severity = p_severity
  WHERE id = p_incident;

  INSERT INTO incident_updates (incident_id, author_id, kind, message)
  VALUES (
    p_incident,
    auth.uid(),
    'command',
    format('Incident severity changed from %s to %s', v_previous, p_severity)
  );

  PERFORM private.audit(
    'incident.severity',
    'incident',
    p_incident::text,
    jsonb_build_object('from', v_previous, 'to', p_severity)
  );
END;
$$;

GRANT EXECUTE ON FUNCTION public.update_incident_severity(uuid, incident_severity) TO authenticated;
