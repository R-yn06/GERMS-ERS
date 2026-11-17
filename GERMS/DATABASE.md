# GERMS Database Documentation

## 1. Database role in the system

Supabase PostgreSQL is the authoritative system of record for GERMS. The
Java desktop client communicates with it over HTTPS through Supabase Auth and
PostgREST. Simple reads use PostgREST table/view queries; privileged or
multi-step writes use PostgreSQL RPC functions.

The client does **not** connect to PostgreSQL directly and does not contain a
service-role key. Every request carries the public anon/publishable key and,
when authenticated, the current user's JWT. PostgreSQL RLS and RPC
authorization remain authoritative even if a caller bypasses the Swing UI.

Apply migrations in exact numeric order:

```text
001 -> 002 -> 003 -> 004 -> 005 -> 006 -> 007 -> 008 -> 009
     -> 010 -> 011 -> 012 -> 013 -> 014 -> 015 -> 016 -> 017
```

## 2. Migration inventory

| Migration | Purpose | Main objects |
|---|---|---|
| `001_core_schema.sql` | Core types, identity, incidents, assignments, notifications, audit, and guest reports | `profiles`, `incidents`, `incident_updates`, `incident_responses`, `incident_assignments`, `assignment_status_history`, `notifications`, `notification_receipts`, `audit_logs`, `guest_reports` |
| `002_rls_policies.sql` | Row Level Security and authorization helpers | `is_admin()`, `is_responder()`, `can_view_incident()`, `public_advisories`, table policies |
| `003_rpc_functions.sql` | Core privileged incident/response operations | `create_incident`, `update_incident`, `publish_incident`, `set_incident_status`, `post_incident_update`, `respond_to_incident`, `assign_responder`, `update_assignment_status`, notification RPCs, response summary |
| `004_reference_seed.sql` | Reference data and legal transition data; certifications and command-position tables are later removed by migration 016 | service divisions, classifications, transition data |
| `005_response_teams.sql` | Response teams and team assignment | `response_teams`, `assign_incident_team` |
| `006_operations_resources.sql` | Evacuation centers and the original disaster-resource table, later removed by migration 016 | `evacuation_centers` |
| `007_admin_management.sql` | Server-authorized profile administration | `update_profile_admin`, `deactivate_profile_admin` |
| `008_responder_command.sql` | Responder readiness and individual deployment requests | `admin_set_responder_availability`, `request_responder_deployment` |
| `009_phase3_operations.sql` | Guest-profile trigger, expanded notifications, teams, resources, SITREPs, messaging, contacts, announcements, and operational locations; `notification_preferences` is later removed by migration 016 | `team_members`, `resources`, `resource_allocations`, `field_situation_reports`, `incident_messages`, `emergency_contacts`, `emergency_announcements`; phase-3 RPCs |
| `010_fix_auth_registration_search_path.sql` | Makes Auth signup trigger type resolution and search path explicit | updated `handle_new_user` trigger |
| `011_fix_profile_relationships.sql` | Fixes PostgREST profile relationships and audit actor embedding | profile/audit foreign-key relationship adjustments |
| `012_incident_severity_updates.sql` | Allows authorized severity changes while preserving history and audit | `update_incident_severity` |
| `013_responder_alert_preferences.sql` | Adds responder-controlled deployment-alert preferences and admin broadcast requests | `responder_notification_preferences`, `set_deployment_notification_preference`, `request_all_active_responder_deployments` |
| `014_admin_notification_center.sql` | Adds a separate administrator acknowledgment for shared responder alerts | `admin_notification_receipts`, `admin_acknowledge_notification` |
| `015_responder_self_classification.sql` | Adds responder-selectable classifications and a self-only update RPC | EMS, ERU, EMR, EMT, DMS classifications; `set_own_responder_classification` |
| `016_drop_unused_tables.sql` | Drops tables with no active application or database-routine use; uses RESTRICT so unexpected dependencies abort the migration | `disaster_resources`, `notification_preferences`, `incident_command_assignments`, `incident_command_positions`, `certifications`, `responder_qualifications` |
| `017_admin_responder_classifications.sql` | Adds admin classification editing, responder classification choices, and an incident-scoped sender directory for chat | `update_profile_admin_with_classification`, `set_own_responder_classification`, `incident_message_sender_directory` |

## 3. Enumerated domain values

Defined in `001_core_schema.sql`:

- `user_role`: `admin`, `responder`; migration 009 adds `guest`.
- `incident_status`: `draft`, `published`, `acknowledged`, `mobilizing`,
  `active_response`, `stabilized`, `closed`, `archived`.
- `incident_severity`: `low`, `moderate`, `high`, `critical`.
- `incident_category`: `flood`, `typhoon`, `fire`, `landslide`,
  `vehicular_accident`, `medical_emergency`, `search_and_rescue`,
  `mass_casualty`, `evacuation`, `relief_operation`, `other`.
- `response_decision`: `accepted`, `declined`.
- `assignment_status`: `assigned`, `preparing`, `en_route`, `arrived`,
  `on_task`, `completed`, `released`, `cancelled`.
- `availability_status`: `available`, `standby`, `unavailable`, `off_duty`.

The legal incident and assignment transitions are stored as data in
`incident_status_transitions` and `assignment_status_transitions`, seeded by
`004_reference_seed.sql`. The Java model also validates the main incident
forward lifecycle and blocks `mobilizing` until a response team is assigned.

## 4. Entity groups and relationships

```text
auth.users 1--1 profiles
profiles 1--* incidents              (created_by / published_by)
profiles *--* response_teams         (through team_members)
response_teams 1--* incidents        (response_team_id)
incidents 1--* incident_updates
incidents 1--* incident_responses
incidents 1--* incident_assignments
incident_assignments 1--* assignment_status_history
incidents 1--* notifications
resources 1--* resource_allocations *--1 incidents
incidents 1--* field_situation_reports
incidents 1--* incident_messages
profiles 1--1 responder_availability
profiles 1--1 responder_locations
profiles 1--1 responder_notification_preferences
```

### Identity and responder readiness

- `profiles` references `auth.users(id)` with cascade deletion.
- A signup trigger creates a profile using Auth metadata and assigns the
  `guest` role after migration 009.
- `service_divisions` and `responder_classifications` describe responder
  capability.
- `profile_divisions` and `profile_classifications` connect people to those
  references. Certification and qualification tables were removed in
  migration 016 because the current application does not use them.
- `responder_availability` stores readiness.
- `responder_locations` stores the latest opt-in operational point, accuracy,
  source, sharing flag, and timestamp. It is not a continuous tracking log.

### Incidents and history

`incidents` stores the current operational snapshot: incident number, title,
description, category, severity, status, location, coordinates, public
visibility, commander, publication, and closure information.

`incident_updates` is the chronological history of status changes, notes,
field updates, and command entries. `incident_requirements` records requested
division/classification/headcount.

### Response and dispatch

- `incident_responses` records responder accept/decline decisions.
- `incident_assignments` records confirmed deployments.
- `assignment_status_history` preserves assignment progress.
- A partial unique index prevents duplicate active assignments for the same
  responder and incident. Cross-incident conflict rules are additionally
  enforced by the assignment RPC and system settings.
- `response_teams` and `team_members` represent squads, leaders, and rosters.
- Incident-command position tables were removed in migration 016 because the
  current application does not use them.

### Operations and public information

- `resources` and `resource_allocations` track equipment, quantity,
  availability, condition, incident allocation, release, and consumption.
- `evacuation_centers` stores shelters, capacity, occupancy, status, and
  location.
- `field_situation_reports` stores situation, field conditions, affected
  people, casualties, medical/resource needs, urgency, location, and admin
  feedback.
- `incident_messages` stores persistent incident-channel communication.
- `emergency_contacts` stores public hotlines, hospitals, LGUs, and partners.
- `emergency_announcements` stores public or operational advisories.
- `guest_reports` stores preliminary reports with `unverified`, `verified`,
  `rejected`, or `converted` status.

### Notifications and preferences

- `notifications` stores recipient, incident, kind, priority, title, body,
  and creation time.
- `notification_receipts` tracks observed and acknowledged state.
- `responder_notification_preferences` (migration 013) specifically controls
  whether a responder receives deployment requests.

The desktop shell currently checks unacknowledged notifications on a
five-second timer. The checked-in client does not implement a Supabase
Realtime subscription or websocket channel.

## 5. RLS and server-side authorization

RLS is enabled on the core and phase-3 tables. Policies are intentionally
different per data type:

- Profiles: users can read/update their own allowed profile fields; admins
  can manage profiles through authorized operations.
- Reference data: authenticated users can read divisions, classifications,
  and transition data. Certification and command-position tables were removed
  by migration 016.
- Availability/location: a responder can manage their own row; admins can
  read operational data where policy permits.
- Incidents and incident history: visibility is controlled by
  `can_view_incident(id)`.
- Responses/assignments/history: responders see their own operational rows;
  admins can review command data.
- Notifications/receipts: recipients can see and acknowledge their own
  notifications; admins can review operational notifications.
- Audit logs: administrators can read them; mutation is blocked by the
  append-only trigger.
- Guest reports: anonymous/authenticated callers can insert only an
  unverified, unreviewed report; admin policies control review/update access.
- Resources, allocations, teams, and admin-managed reference operations are
  restricted by admin policies or secured RPCs.
- Emergency contacts and announcements have separate public-read policies
  for rows marked public/published.

The UI `AccessPolicy` only controls navigation. It is not a security
boundary.

## 6. RPC and transaction rules

Privileged operations run as `SECURITY DEFINER` functions with a controlled
`search_path` and server-side role checks. Important operations include:

- Incident creation, update, publication, status change, and severity change.
- Responder response, assignment, assignment-status change, and cancellation.
- Team assignment and responder promotion.
- Availability changes and deployment requests.
- Admin responder-classification changes and incident-scoped message sender details.
- Resource allocation/release.
- SITREP submission and incident messaging.
- Responder location update.
- Notification observation/acknowledgement.
- Deployment-alert preference changes and broadcast requests.

The functions are important because they keep authorization, validation,
locking, related writes, notifications, and auditing in one database
operation. Resource allocation, for example, must decrement availability
atomically rather than relying on a client-side read-then-write sequence.

## 7. Audit and integrity controls

- Foreign keys protect identity, incident, assignment, team, resource, and
  message relationships.
- Check constraints validate coordinates, quantities, statuses, text lengths,
  and allowed categories.
- Unique constraints prevent duplicate memberships, responses, and active
  assignment rows.
- `incident_updates` and `assignment_status_history` preserve operational
  history.
- `audit_logs` is append-only; `private.block_audit_mutation()` rejects
  updates and deletes.
- Privileged RPCs call `private.audit(...)` for command actions.
- Database timestamps provide chronological operational records.

## 8. Backend setup and bootstrap

1. Create a Supabase project.
2. Back up the database, then run migrations `001` through `017` in order.
3. Create/confirm the first Auth user.
4. Set that user's matching `profiles.role` to `admin` using the bootstrap
   SQL in `SETUP.md`.
5. Configure the desktop client with the project URL and public anon/
   publishable key.

The repository includes a generic `RestClient.invokeFunction(...)` method and
the UI references an `admin-create-user` function for administrative account
creation. Any deployed Supabase Edge Function used for that flow must be
configured separately; the checked-in database migrations remain the source of
truth for the PostgreSQL schema and authorization model.
