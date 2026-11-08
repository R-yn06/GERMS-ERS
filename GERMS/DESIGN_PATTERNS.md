# Design Patterns in GERMS

This document catalogs the design patterns implemented in the GERMS application.

| Pattern | Location | Implementation Details |
|---|---|---|
| **Repository** | `services/IncidentRepository`, `SupabaseIncidentRepository`, `repositories/ProfileRepository` | Decouples domain logic from PostgREST/PostgreSQL transport and mapping details. |
| **Builder** | `models/IncidentBuilder` | Enforces mandatory field invariants (title, location, reporter) and constructs immutable `Incident` instances. |
| **State** | `models/IncidentStatus` | Encapsulates legal lifecycle forward transitions (`draft -> published -> acknowledged -> mobilizing -> active_response -> stabilized -> closed -> archived`) and preconditions (`blocker()`). |
| **Observer** | `services/IncidentBus`, `services/IncidentEvent` | Thread-safe in-process event bus using `CopyOnWriteArrayList` delivering model change events to UI subscribers on the Swing Event Dispatch Thread (EDT). |
| **Strategy** | `services/AlarmService` | Selects waveform synthesis parameters (frequencies, duration, volume, decay, warble siren) based on incident severity level (Low, Moderate, High, Critical). |
| **Singleton** | `config/AppContext`, `services/AlarmService` | Centralizes dependency injection wiring and audio line hardware control across the application lifecycle. |
| **Facade** | `services/IncidentService`, `services/AuthService` | Provides unified application services orchestrating repository operations, session management, and UI event broadcasting. |
| **Adapter** | `supabase/RestClient`, `supabase/AuthClient`, `ui/components/OpenStreetMapPanel` | Adapts HTTP/JSON endpoints and OpenStreetMap tile servers into high-level Java records and Swing components. |
| **RBAC Policy** | `security/AccessPolicy`, `security/Module` | Computes permissible operational modules per user role (`Admin`, `Responder`, `Guest`). Backed authoritatively by database Row Level Security and RPCs. |
