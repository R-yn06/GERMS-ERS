# Implementation status: Milestone 1, step 1 to 3 of the build order

Legend: Written = code exists; **Unverified** = not compiled or run (the authoring sandbox had no JDK compiler, Maven, Postgres, or network).

| Area | Status |
|---|---|
| Maven project, FlatLaf light/dark theme with persistence, red brand palette | Written, unverified |
| Login (Supabase Auth password grant), token refresh, sign out, guest mode | Written, unverified |
| Role-aware sidebar (16 admin modules, responder subset, guest subset) | Written, unverified |
| Dashboard with real RLS-filtered incident counts | Written, unverified |
| Placeholder panels for later modules, labeled "Not yet implemented" | Written |
| Migrations 001-004: schema, RLS, RPCs (create/update/publish/status, respond, assign, deployment status, notification receipts, summary), Realtime publication | Written, unverified on Postgres |
| `AccessPolicy` and config-key-rejection unit tests | Written, not run |

## Not yet built (next steps in order)
4. Incident form, publish flow, incident list/detail, admin Dispatch panel
5. `RealtimeClient` (Phoenix protocol over `java.net.http.WebSocket`) with 5 s polling fallback, `NotificationService`, audible alerts
6. Responder accept/decline UI, admin assignment UI
7. Deployment status UI, audit log viewer, more tests

## Externally dependent
- Supabase project and applied migrations.
- Realtime: tables are added to `supabase_realtime` by migration 003; confirm in Dashboard > Database > Publications.
- `admin-create-user` Edge Function (TypeScript, server-side, service-role) is **not written yet**. Until then, create accounts in the Supabase dashboard and insert a `profiles` row.
- Maps need OpenJFX (Phase 3).

## Known design decisions
- One active assignment per responder is enforced in `assign_responder()` from `system_settings.one_active_assignment` (a partial index cannot read settings). A partial unique index still prevents duplicate active rows per incident and responder.
- Refresh tokens are held in memory only; the user signs in again after restart.
- Notification eligibility at publish: active responder, availability available/standby (or unset), division match when the incident lists requirements. Classification and certification checks arrive with Phase 3 qualification verification.
