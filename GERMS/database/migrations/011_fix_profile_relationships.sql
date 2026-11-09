-- GERMS 011: Make profile embeds unambiguous in PostgREST.
-- NOT VALID preserves historical audit rows whose actor no longer has a profile,
-- while enforcing the relationship for new or changed non-null actor IDs.

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'audit_logs_actor_id_fkey'
      AND conrelid = 'public.audit_logs'::regclass
  ) THEN
    ALTER TABLE public.audit_logs
      ADD CONSTRAINT audit_logs_actor_id_fkey
      FOREIGN KEY (actor_id) REFERENCES public.profiles(id) NOT VALID;
  END IF;
END;
$$;

NOTIFY pgrst, 'reload schema';
