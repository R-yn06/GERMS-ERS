# Implementation status: Milestone 1, step 1 to 3 of the build order

Legend: Written = code exists; **Unverified** = not tested against a live Supabase project.

| Area | Status |
|---|---|
| Maven project, FlatLaf light/dark theme with persistence, red brand palette | Written, unverified |
| Login (Supabase Auth password grant), token refresh, sign out, guest mode | Written, unverified |
| Role-aware sidebar (16 admin modules, responder subset, guest subset) | Written, unverified |
| Supabase-backed incident list, detail, timeline, create/publish/status workflow, and response-team assignments | Written; live database requires migrations 001-005 |
| Guest reports persisted to `guest_reports` | Written; live database requires migrations 001-004 |
| Dashboard with RLS-filtered Supabase incident counts | Written; live database unverified |
| Placeholder panels for later modules, labeled "Not yet implemented" | Written |
| Migrations 001-005: schema, RLS, RPCs, reference data, and response teams | Written, unverified on Postgres |
| Java 21 compilation and `AccessPolicy` tests | Verified in the workspace |

## Not yet built (next steps in order)
1. `RealtimeClient` (Phoenix protocol over `java.net.http.WebSocket`) with 5 s polling fallback, `NotificationService`, audible alerts
2. Responder accept/decline UI, admin individual-responder assignment UI
3. Deployment status UI, audit log viewer, more tests

## Externally dependent
- Supabase project and applied migrations 001-005. The desktop client cannot apply schema migrations itself.
- Realtime: tables are added to `supabase_realtime` by migration 003; confirm in Dashboard > Database > Publications.
- `admin-create-user` Edge Function (TypeScript, server-side, service-role) is **not written yet**. Until then, create accounts in the Supabase dashboard and insert a `profiles` row.
- Maps need OpenJFX (Phase 3).

## Known design decisions
- One active assignment per responder is enforced in `assign_responder()` from `system_settings.one_active_assignment` (a partial index cannot read settings). A partial unique index still prevents duplicate active rows per incident and responder.
- Refresh tokens are held in memory only; the user signs in again after restart.
- Notification eligibility at publish: active responder, availability available/standby (or unset), division match when the incident lists requirements. Classification and certification checks arrive with Phase 3 qualification verification.
