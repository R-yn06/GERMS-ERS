# Database

Supabase Postgres is the system of record. Apply SQL migrations in numeric order from `database/migrations/` (001 through 005). They define user profiles and roles, responder divisions/classifications/qualifications/availability, incidents and requirements, incident updates and volunteer responses, assignments and assignment history, notifications and receipts, audit logs, settings, guest reports, response teams, row-level security, lifecycle transition data, and privileged RPCs.

Primary relationships include `profiles.id -> auth.users.id`; incident-owned records reference `incidents.id`; responses and assignments reference both an incident and responder profile. Audit rows are append-only. The partial unique index prevents duplicate active assignments for the same incident/responder; `assign_responder` serializes assignments per responder and enforces the configurable one-active-assignment rule.

Guest submissions use the `guest_reports` table and do not become verified incidents automatically. `public_advisories` exposes a limited public projection. The client must use only the Supabase anon/publishable key; never embed a service role key.

See `database/seed_data.sql` for first-admin bootstrap guidance. No live project was available for migration validation in this workspace.
