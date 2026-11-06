# Design patterns in GERMS

This document describes patterns present in the current implementation, not a claim that all patterns in the Phase 2 brief are complete.

| Pattern | Location | Purpose |
|---|---|---|
| Repository | `services/IncidentRepository`, `SupabaseIncidentRepository` | Keeps incident workflow logic separate from PostgREST persistence. |
| Builder | `models/IncidentBuilder` | Constructs and validates incident snapshots from form inputs. |
| Observer | `services/IncidentBus`, `IncidentEvent` | Announces incident changes to in-process UI subscribers. |
| State | `models/IncidentStatus` | Associates legal next steps and transition blockers with lifecycle states. The database independently validates transitions. |
| Facade / application service | `services/IncidentService` | Offers screens a small API for report, assign-team, and advance workflows. |
| Adapter | `supabase/AuthClient`, `RestClient`, `SupabaseIncidentRepository` | Adapts Supabase Auth/PostgREST to the application interfaces. |
| RBAC policy | `security/AccessPolicy` | Filters navigation by role. It complements, but does not replace, database authorization. |

Dispatch assignment confirmation delegates to the database `assign_responder` RPC, keeping race-sensitive eligibility and assignment checks authoritative. The planned realtime notification strategies, command objects, and classification management patterns are not yet implemented.
