-- GERMS 021: allow administrators to edit and delete managed records.

GRANT UPDATE, DELETE ON resources, evacuation_centers, response_teams TO authenticated;

CREATE POLICY response_teams_admin_update ON response_teams
  FOR UPDATE TO authenticated
  USING (public.is_admin())
  WITH CHECK (public.is_admin());

CREATE POLICY response_teams_admin_delete ON response_teams
  FOR DELETE TO authenticated
  USING (public.is_admin());
