# Implementation status: Phase 2 foundations (partial)

Legend: Written = code exists; **Unverified** = not tested against a live Supabase project.

| Area | Status |
|---|---|
| Maven project, FlatLaf light/dark theme with persistence, red brand palette | Written, unverified |
| Login (Supabase Auth password grant), token refresh, sign out, guest mode | Written, unverified |
| Role-aware sidebar (16 admin modules, responder subset, guest subset) | Written, unverified |
| Supabase-backed incident list, detail, timeline, create/publish/status workflow, and response-team assignments | Written; live database requires migrations 001-005 |
| Administrator dispatch response monitoring and assignment confirmation | Written; uses `incident_response_summary` and `assign_responder` RPCs; live Supabase behavior unverified |
| Responder accept/decline action in incident view | Written; uses `respond_to_incident`; live Supabase behavior unverified |
| Guest reports persisted to `guest_reports` | Written; live database requires migrations 001-004 |
| Dashboard with RLS-filtered Supabase incident counts | Written; live database unverified |
| Live operations map data view | Written; displays authoritative incident coordinates and status; external map tiles are not bundled |
| Responder and user management | Written; administrator can create accounts through the Edge Function, edit profiles, and deactivate accounts through secured RPCs |
| Reports and analytics data view | Written; filterable incident report table backed by Supabase |
| Evacuation centers and disaster resources views | Written; requires migration 006 |
| Migrations 001-006: schema, RLS, RPCs, reference data, response teams, and operations resources | Written, unverified on Postgres |
| Java 21 compilation and `AccessPolicy` tests | Verified in the workspace |

## Not yet built (next steps in order)
1. Notification center with severity-aware audible alerts and platform delivery configuration
2. Server-side account provisioning Edge Function and write forms for responder classifications/resources
3. Deployment status UI, audit log viewer, and more workflow tests

## Externally dependent
- Supabase project and applied migrations 001-007. The desktop client cannot apply schema migrations itself.
- Realtime: tables are added to `supabase_realtime` by migration 003; confirm in Dashboard > Database > Publications.
- `admin-create-user` Edge Function (TypeScript, server-side, service-role) is **not written yet**. Until then, create accounts in the Supabase dashboard and insert a `profiles` row.
- Maps need OpenJFX (Phase 3).

## Known design decisions
- One active assignment per responder is enforced in `assign_responder()` from `system_settings.one_active_assignment` (a partial index cannot read settings). A partial unique index still prevents duplicate active rows per incident and responder.
- Refresh tokens are held in memory only; the user signs in again after restart.
- Notification eligibility at publish: active responder, availability available/standby (or unset), division match when the incident lists requirements. Classification and certification checks arrive with Phase 3 qualification verification.
- Dispatch monitors the existing incident volunteer responses and confirms accepted responders using the authoritative `assign_responder` database function. It polls while visible (5 seconds); this is not Supabase Realtime and does not deliver background notifications.
- This is a Java/Swing project; there is no Godot project configuration or Godot source in this repository.
- Account creation requires deploying `supabase/functions/admin-create-user` with Supabase CLI and configuring its project secrets.
