-- GERMS 019: allow administrators to create response teams.

GRANT INSERT ON response_teams TO authenticated;

CREATE POLICY response_teams_admin_insert ON response_teams
  FOR INSERT TO authenticated
  WITH CHECK (public.is_admin());
