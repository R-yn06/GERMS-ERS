# GERMS Database Documentation

Supabase PostgreSQL is the authoritative system of record.
Apply SQL migrations in numeric order from `database/migrations/` (001 through 012).

## Migrations Overview

| Migration | Purpose | Key Tables / Objects |
|-----------|---------|----------------------|
| `001_core_schema.sql` | Identity, Core Incidents, Assignments, Audit | `profiles`, `service_divisions`, `responder_classifications`, `incidents`, `incident_updates`, `incident_responses`, `incident_assignments`, `notifications`, `audit_logs`, `guest_reports` |
| `002_rls_policies.sql` | Row Level Security & Helpers | RLS enabled on all public tables; `is_admin()`, `is_responder()`, `can_view_incident()`, `public_advisories` view |
| `003_rpc_functions.sql` | Privileged RPC Operations | `create_incident`, `publish_incident`, `set_incident_status`, `assign_responder`, `update_assignment_status`, `acknowledge_notification`, `incident_response_summary` |
| `004_reference_seed.sql` | Static Reference Data | Divisions (DMS, SAFETY, VOLUNTEER, etc.), Classifications, ICS Positions, Status transitions |
| `005_response_teams.sql` | Response Teams | `response_teams`, `assign_incident_team` RPC |
| `006_operations_resources.sql` | Operational Resources & Centers | `evacuation_centers`, `disaster_resources` |
| `007_admin_management.sql` | Administrative Profile Controls | `update_profile_admin`, `deactivate_profile_admin` |
| `008_responder_command.sql` | Responder Command & Readiness | `admin_set_responder_availability`, `request_responder_deployment` |
| `009_phase3_operations.sql` | Phase 3 Operations, SITREPs, Comms, Alarms | `team_members`, `resources`, `resource_allocations`, `field_situation_reports`, `incident_messages`, `emergency_contacts`, `emergency_announcements`, `notification_preferences`, `promote_to_responder`, `allocate_resource`, `release_resource`, `submit_field_situation_report`, `send_incident_message`, `update_responder_location` |
| `010_fix_auth_registration_search_path.sql` | Auth Signup Trigger Fix | Schema-qualifies `user_role` and pins the signup trigger search path so Supabase Auth can create guest profiles |
| `011_fix_profile_relationships.sql` | PostgREST Profile Relationships | Adds the audit actor relationship needed for profile embeds; retains historical audit rows with `NOT VALID` |
| `012_incident_severity_updates.sql` | Authorized Incident Severity Updates | Adds `update_incident_severity`, restricted to administrators and responders with an active assignment; records incident history and audit entries |

## Key Entity Relationships

- **Identity**: `profiles.id -> auth.users(id) ON DELETE CASCADE`. Supports roles: `admin`, `responder`, `guest`.
- **Classifications**: `profile_classifications(profile_id, classification_id) -> responder_classifications(id)`.
- **Teams**: `response_teams(id)` has `leader_id -> profiles(id)`. Squad members stored in `team_members(team_id, profile_id)`.
- **Incidents**: `incidents(id)` references `created_by -> profiles(id)`, `published_by -> profiles(id)`, `response_team_id -> response_teams(id)`.
- **Responses & Deployments**: `incident_responses(incident_id, responder_id)`, `incident_assignments(incident_id, responder_id)`. Partial unique index ensures at most one active assignment per responder.
- **Resource Inventory & Allocation**: `resources(id)` tracks equipment quantity, available quantity, condition, and status. `resource_allocations(resource_id, incident_id)` tracks atomic allocations.
- **Field SITREPs**: `field_situation_reports(id)` links to `incident_id`, `reporter_id`, and `team_id`, recording casualties, conditions, and urgent medical needs.
- **Emergency Comms**: `incident_messages(incident_id, sender_id)` provides persistent channel messages.
- **Contacts & Advisories**: `emergency_contacts` and `emergency_announcements` provide public and operational coordination data.

## Server-Side Security Model
1. **Default Deny**: All tables have RLS enabled with default deny.
2. **SECURITY DEFINER Functions**: Privileged operations pin `search_path` to trusted schemas (or an empty path with fully qualified names) and verify authorization via `private.require_admin()` or `is_responder()`.
3. **Audit Trail**: All privileged actions write atomically to `audit_logs`, which is append-only (mutation blocked by trigger).
4. **Client Anon Key Only**: The Java desktop client operates exclusively with the public anon key and authenticated session JWTs.
