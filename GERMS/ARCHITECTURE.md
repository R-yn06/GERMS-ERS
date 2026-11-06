# GERMS architecture

GERMS is a Java 25 Swing desktop client. The Supabase project is the authoritative data layer: Auth issues user JWTs, Postgres enforces row-level authorization and privileged RPCs, and PostgREST exposes the permitted operations. There is no Godot runtime in this repository.

## Layers

- `ui/`: Swing presentation. `Shell` selects role-allowed modules; incident and dispatch screens call application services or the authenticated REST client away from the Swing event thread.
- `services/`: application workflows (`IncidentService`), repository boundaries, and in-process incident events.
- `models/`: immutable incident, profile, role, timeline, and response-team values. `IncidentStatus` owns legal forward transitions for the client.
- `repositories/` and `supabase/`: profile persistence, Auth, JWT refresh, and PostgREST/RPC transport.
- `security/`: role-to-module presentation policy. This is navigation only; database RLS and RPC authorization are authoritative.
- `database/migrations/`: schema, RLS, lifecycle data, audit records, and `SECURITY DEFINER` RPCs.

## Key flows

Incident creation and transition operations pass through `IncidentService` and `SupabaseIncidentRepository`. Dispatch reads the admin-only response summary and incident responses, then invokes `assign_responder`; the RPC verifies admin role, responder status, availability, existing assignments, and uniqueness inside the database transaction. The UI refreshes the selected incident every five seconds while the dispatch screen is visible.

Guest reports are stored in `guest_reports`, separate from verified incidents. Public advisory reads use the narrow `public_advisories` view. Account roles are never trusted from a local UI check for protected database writes.

## Runtime limitations

Dispatch polling is foreground-only. A notification record or audible/background alert pipeline is not yet implemented in the desktop client. Maps, classification administration, credential provisioning, and several role modules remain incomplete. Apply migrations 001-005 before connecting a configured Supabase project.
