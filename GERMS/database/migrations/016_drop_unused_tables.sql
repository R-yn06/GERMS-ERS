-- GERMS 016: Drop tables with no active use in the application or database routines.
-- Back up the database before applying. DROP TABLE intentionally uses RESTRICT
-- (the default) so unexpected dependencies stop this migration instead of cascading.

drop table if exists public.disaster_resources;
drop table if exists public.notification_preferences;
drop table if exists public.incident_command_assignments;
drop table if exists public.responder_qualifications;
drop table if exists public.incident_command_positions;
drop table if exists public.certifications;
