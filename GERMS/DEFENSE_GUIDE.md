# GERMS Defense Guide

## 1. One-minute project explanation

**GERMS** means **Gudo Emergency Response Management System**. It is a desktop
emergency incident command and responder-operations system intended for
Philippine Red Cross emergency response operations, particularly the Davao City
Chapter.

The problem it addresses is fragmented emergency coordination: reports,
verification, dispatch, responder availability, team deployment, field
updates,
resources, evacuation centers, communication, and audit history are difficult
to manage when they are kept in separate tools or manual records.

GERMS gives three user experiences:

1. **Guest/public user** - submit a preliminary emergency report and see
   public advisories, evacuation centers, and emergency contacts.
2. **Responder** - receive alerts, respond to deployments, update mission
   status, share an opted-in operational location, submit SITREPs, and
   communicate with command.
3. **Administrator/incident commander** - verify and publish incidents,
   dispatch responders and teams, manage resources and shelters, monitor the
   live operations map, communicate with teams, analyze operations, and review
   the audit trail.

The system is a **Java 25 Swing desktop client** connected to a **Supabase
backend**. Supabase provides authentication, PostgreSQL persistence,
PostgREST HTTP access, Row Level Security (RLS), and server-side RPC functions.
The client uses only the public anon/publishable key; the authenticated user's
JWT supplies identity and permissions.

---

## 2. Problem, objectives, and scope

### Problem statement

During an emergency, responders need one reliable operational picture. A
system that only records reports is insufficient: command must be able to
verify, prioritize, assign, communicate, track progress, account for
resources, and preserve a defensible history of decisions.

### General objective

Build a secure emergency response management system that centralizes incident
reporting, command, responder deployment, operational resources, field
reporting, communication, and public safety information.

### Specific objectives

- Accept public and authenticated incident reports.
- Give command a controlled incident lifecycle from report to closure.
- Prevent unauthorized access through database-enforced RBAC and RLS.
- Coordinate responders, teams, availability, assignments, and mission status.
- Provide map-based incident, responder, shelter, and resource visibility.
- Deliver persistent notifications and severity-based audible alarms.
- Record field conditions, casualties, urgent needs, and command feedback.
- Track evacuation-center capacity and emergency-resource availability.
- Provide operational analytics, CSV export, and append-only auditing.

### In scope

- Desktop client UI and role-based modules.
- Supabase Auth and PostgreSQL data/security model.
- OpenStreetMap tile and geocoding integrations.
- Emergency notification/alarm behavior.
- Operational workflows and auditability.

### Out of scope or deliberately limited

- Continuous GPS tracking: responders explicitly share point updates; the
  client does not silently track a responder.
- Automatic dispatch decisions: command confirms assignments.
- Service-role credentials in the client.
- Full public social-media or SMS gateway integration.
- Autonomous emergency decisions: the system supports human command.

---

## 3. Complete architecture

```text
+----------------------+       HTTPS/JWT        +----------------------------+
| Java 25 Swing Client | ---------------------> | Supabase                   |
|                      |                        |                            |
|  UI / Shell          | <--- PostgREST JSON -->| Auth (GoTrue)               |
|  Services            | <--- RPC calls ------->| PostgreSQL                  |
|  Models              |                        |  - tables and relationships |
|  Rest/Auth clients   |                        |  - RLS policies             |
|  Session manager     |                        |  - SECURITY DEFINER RPCs    |
|  AccessPolicy        |                        |  - audit triggers           |
+----------+-----------+                        +-------------+--------------+
           |                                                  |
           | HTTPS                                            |
           v                                                  v
+----------------------+                        +----------------------------+
| OpenStreetMap       |                        | External/location services |
| map tile server     |                        | Nominatim geocoding        |
|                     |                        | IP approximate location    |
+----------------------+                        +----------------------------+
```

### Layer responsibilities

#### Presentation layer: `com.gudo.germs.ui`

Swing panels display data, collect user input, show status/errors, and invoke
application services or authorized RPCs. `MainWindow` switches between login
and the authenticated `Shell`. `Shell` creates the role-filtered sidebar and
lazily creates module panels.

The UI policy is a **convenience and UX filter**, not the security boundary.
Hiding a menu item is never treated as authorization.

#### Application/service layer: `com.gudo.germs.services`

- `AuthService` handles login, registration, logout, session/profile loading.
- `IncidentService` validates and coordinates incident operations, lifecycle
  transitions, response decisions, and event publication.
- `AlarmService` manages audio eligibility, deduplication, playback, muting,
  and dismissal.
- `IncidentBus`/`IncidentEvent` notify interested UI components of changes.
- `GeoLocationService` validates coordinates, searches landmarks, reverse
  geocodes, and provides explicitly requested approximate IP location.

#### Domain/model layer: `com.gudo.germs.models`

Immutable records such as `Incident`, `Profile`, `ResponseTeam`, and
`TimelineEntry` represent application data. `IncidentBuilder` validates
required fields. `IncidentStatus` owns the legal forward lifecycle and
transition blockers.

#### Transport/persistence layer

- `SupabaseHttp` is the single low-level HTTP gateway.
- `AuthClient` calls Supabase Auth.
- `RestClient` calls PostgREST tables, RPC functions, and generic Edge
  Functions.
- `SessionManager` stores the session in memory and refreshes tokens shortly
  before expiry.
- `SupabaseIncidentRepository` maps JSON rows to immutable domain models and
  hides PostgREST/RPC details from `IncidentService`.
- `ProfileRepository` loads the active profile.

#### Configuration and composition

`AppContext` is the application composition root. It constructs configuration,
HTTP, session, REST, auth, repository, and incident-service objects once and
shares them with the UI.

Configuration precedence is:

1. `GERMS_SUPABASE_URL` and `GERMS_SUPABASE_ANON_KEY` environment variables.
2. `application.properties` in the working directory.
3. Classpath `application.properties`.

The client rejects service-role JWTs and `sb_secret_...` keys.

---

## 4. Frontend/UI modules

### Shared entry and navigation

- `MainWindow`: login/registration card versus authenticated shell.
- `Shell`: top bar, role-filtered sidebar, theme control, current user, logout,
  lazy-loaded module cards, and five-second alert polling.
- `ThemeManager`, `GermsColors`, `SurfacePanel`, `StatCard`, and `StatusBadge`:
  consistent visual design and light/dark theme behavior.

### Administrator modules

1. **Command Dashboard** - operational counts, active incidents, responder
   status, and summary information.
2. **Incident Management** - view, create, filter, verify, publish, update
   severity, assign a team, and advance lifecycle.
3. **Dispatch Center** - review responses, confirm assignments, prevent
   responder conflicts, use emergency override when authorized, cancel
   deployments, and resolve operations.
4. **Live Operations Map** - incidents, opted-in responders, evacuation
   centers, and resources using OpenStreetMap.
5. **User & Responder Management** - edit/deactivate profiles, promote guests,
   assign classifications, and control availability.
6. **Team & Squad Management** - create teams, designate leaders, manage
   rosters, and deploy teams.
7. **Resource & Equipment** - inventory, condition, available quantity,
   allocation, release, and consumed/returned tracking.
8. **Evacuation Centers** - shelter location, capacity, occupancy, and
   operating status.
9. **Field Situation Reports** - review field reports and command feedback.
10. **Emergency Communication** - incident channels, broadcasts, urgency, and
    persistent message history.
11. **Emergency Contacts** - hotlines, hospitals, LGUs, and partner agencies.
12. **Reports & Analytics** - severity/category breakdowns, deployment
    completion, and CSV export.
13. **Activity & Audit Logs** - chronological, append-only command history.
14. **System Settings** - theme and operational preferences.

### Responder modules

- **Dashboard**
- **Incident Management** for assigned/visible operational incidents
- **Live Operations Map**
- **Field Situation Reports**
- **Emergency Communication**
- **Emergency Contacts**
- **My Deployments**: accept/decline, acknowledge alerts, and advance
  `assigned -> preparing -> en_route -> arrived -> on_task -> completed`.
- **My Assigned Team**: team leader, roster, and assignment context.
- **Settings**: including deployment-notification preference.

### Guest modules

- Public announcements and weather/disaster advisories.
- Submit preliminary incident report.
- Track submitted reports.
- View public evacuation centers.
- View emergency contacts.
- Settings/theme.

---

## 5. End-to-end data and request flow

### Example: administrator publishes an incident

1. The admin clicks **Publish** in `IncidentPanel`.
2. The UI starts a `BackgroundTask` so the Swing Event Dispatch Thread is not
   blocked by network I/O.
3. `IncidentService` validates the current incident and legal next state.
4. `SupabaseIncidentRepository` calls the `publish_incident` RPC through
   `RestClient`.
5. `SupabaseHttp` sends:
   - the public anon key as `apikey`;
   - the user's access JWT as `Authorization`;
   - the JSON RPC body over HTTPS.
6. PostgreSQL/RPC checks the authenticated role, changes the incident, writes
   history/audit information, and creates eligible notifications.
7. The repository reloads the updated row and maps it to an immutable
   `Incident`.
8. `IncidentService` publishes an `IncidentEvent`.
9. Subscribers refresh their UI on the Swing EDT.
10. The shell's alert watcher sees an unacknowledged notification and
    `AlarmService` plays the appropriate severity alarm once.

### Why network work uses `BackgroundTask`

Swing must keep long-running work off the Event Dispatch Thread. The utility
uses `SwingWorker`: `doInBackground()` performs the HTTP/database work, and
`done()` delivers success or error back to the UI thread. This prevents a
frozen interface while preserving Swing thread safety.

### Error path

`SupabaseHttp` converts non-2xx responses into `SupabaseException` with the
HTTP status and server message. The UI displays an actionable error. Transport
errors are not silently converted into successful results.

---

## 6. Database and backend architecture

PostgreSQL is the authoritative system of record. Apply the migrations in
numeric order:

| Migration | Purpose |
|---|---|
| 001 | Core types, identity, incidents, responses, assignments, notifications, audit, guest reports |
| 002 | RLS policies, role helpers, incident visibility, public advisories |
| 003 | Incident, response, assignment, notification, and summary RPCs |
| 004 | Reference data: divisions, classifications, positions, transitions |
| 005 | Response teams and team assignment |
| 006 | Evacuation centers and disaster resources |
| 007 | Admin profile updates and deactivation |
| 008 | Responder availability and deployment requests |
| 009 | Phase 3 teams, resources, allocations, SITREPs, messaging, contacts, announcements, preferences, locations |
| 010 | Auth signup trigger search-path/type-resolution fix |
| 011 | Profile relationship and audit-actor relationship fix for PostgREST |
| 012 | Authorized incident severity updates with history/audit |
| 013 | Responder alert preferences and broadcast deployment requests to eligible responders |

### Core types

- Roles: `admin`, `responder`; guest behavior is represented by the client
  and public/anonymous report policies.
- Incident status:
  `draft -> published -> acknowledged -> mobilizing -> active_response ->
  stabilized -> closed -> archived`.
- Severity: `low`, `moderate`, `high`, `critical`.
- Categories: flood, typhoon, fire, landslide, vehicular accident, medical
  emergency, search and rescue, mass casualty, evacuation, relief operation,
  other.
- Response decision: accepted or declined.
- Assignment status:
  `assigned`, `preparing`, `en_route`, `arrived`, `on_task`, `completed`,
  `released`, `cancelled`.
- Availability: `available`, `standby`, `unavailable`, `off_duty`.

### Main entities

```text
auth.users 1--1 profiles
profiles 1--* incidents (creator/publisher)
profiles *--* response_teams (through team_members)
response_teams 1--* incidents
incidents 1--* incident_updates
incidents 1--* incident_responses
incidents 1--* incident_assignments
incident_assignments 1--* assignment_status_history
incidents 1--* notifications
resources 1--* resource_allocations *--1 incidents
incidents 1--* field_situation_reports
incidents 1--* incident_messages
profiles 1--* responder_locations
profiles 1--1 responder_notification_preferences
```

Important tables include:

- **Identity**: `profiles`, divisions, classifications, qualifications,
  availability, locations.
- **Incidents**: `incidents`, requirements, updates, status transitions,
  command positions/assignments.
- **Response**: responses, assignments, assignment history, teams, members.
- **Operations**: resources, allocations, evacuation centers.
- **Communication**: notifications, receipts, SITREPs, incident messages.
- **Public information**: guest reports, emergency contacts, announcements,
  public advisories.
- **Governance**: audit logs and system settings.

### Transactional integrity examples

- `SECURITY DEFINER` RPCs centralize privileged writes.
- Resource allocation is performed atomically so available quantity cannot be
  decremented by two successful allocations incorrectly.
- A partial unique rule prevents more than one active assignment for a
  responder where applicable.
- Audit rows are append-only; an update/delete trigger blocks mutation.
- Incident status and assignment status transitions are constrained by database
  transition data and server-side checks.
- Severity changes are restricted to administrators and responders with an
  active assignment.

---

## 7. Security model

### Defense-in-depth

1. **Authentication**: Supabase Auth verifies email/password and issues JWTs.
2. **Session protection**: access and refresh tokens exist only in memory;
   `SessionManager` refreshes near expiration.
3. **Client key restriction**: the desktop app accepts only the public
   anon/publishable key and rejects privileged credentials.
4. **UI RBAC filtering**: `AccessPolicy` controls which modules appear.
5. **Database RLS**: PostgreSQL checks every table read/write using
   `auth.uid()`, `is_admin()`, `is_responder()`, and incident visibility
   rules.
6. **RPC authorization**: privileged operations use server-side functions
   such as `private.require_admin()`.
7. **Auditability**: command actions are recorded and audit history is
   immutable.

### Key defense answer

> The UI is not trusted. Even if a user modifies the client or calls the API
> directly, PostgreSQL RLS and authorized RPC functions still decide whether
> the action is allowed. The client never contains a service-role key.

### Privacy-aware location behavior

Responder location sharing is opt-in. The system records an operational point,
source, accuracy when available, sharing flag, and timestamp. It is not
designed as covert continuous surveillance.

---

## 8. Main workflows to demonstrate

### A. Guest report to verified incident

1. Enter guest mode or register.
2. Submit title, category, severity, location, and optional map coordinates.
3. Store the report in `guest_reports` as unverified.
4. Admin reviews it and verifies/publishes it.
5. Eligible responders receive a notification/deployment request.

### B. Incident command lifecycle

1. Create incident.
2. Publish after verification.
3. Acknowledge response.
4. Assign a response team.
5. Move to mobilizing; the client blocks this without a team.
6. Move to active response.
7. Stabilize the situation.
8. Close with a required closure summary.
9. Archive when appropriate.

### C. Responder deployment

1. Admin requests deployment or sends a broadcast request.
2. Available/standby active responders receive notifications, subject to their
   preference.
3. Responder accepts or declines.
4. Admin confirms the assignment.
5. Responder advances mission status through the operational sequence.
6. Command can monitor status and resolve/cancel the operation.

### D. Resources and shelters

1. Admin registers a resource and quantity.
2. Allocate it to an active incident.
3. Verify available quantity decreases atomically.
4. Release or mark consumed when appropriate.
5. Register an evacuation center and update occupancy.
6. Public users see shelter availability subject to public policies.

### E. Field reporting and communication

1. Deployed responder submits a SITREP.
2. Include casualties, field conditions, urgent flag, medical needs, and
   optional selected location.
3. Command reviews the report and sends feedback.
4. Use the incident communication channel for tactical messages/broadcasts.
5. History remains available for operational accountability.

### F. Emergency alarm

1. Shell polls unacknowledged notifications for authenticated staff.
2. It respects responder notification preference.
3. `AlarmService` checks active responder status, audible preference, mute
   state, and notification ID deduplication.
4. It synthesizes PCM audio:
   - low: gentle 880 Hz chime;
   - moderate: ascending 659/880 Hz alert;
   - high: three-pulse 784/1046 Hz cadence;
   - critical: 900-1350 Hz warbling siren, repeated up to five cycles.
5. Silence, acknowledgement, muting, or panel removal stops playback.

---

## 9. Design patterns and why they matter

| Pattern | Example | Reason |
|---|---|---|
| Repository | `IncidentRepository` and Supabase implementation | Keeps domain logic independent of HTTP/JSON persistence |
| Builder | `IncidentBuilder` | Validates required fields before constructing an immutable incident |
| State | `IncidentStatus` | Encapsulates legal transitions and lifecycle blockers |
| Observer | `IncidentBus` and `IncidentEvent` | Refreshes subscribers after model changes |
| Strategy | `AlarmService` severity selection | Selects a different sound strategy per severity |
| Singleton | `AppContext`, `AlarmService` | Provides controlled application-wide context/audio state |
| Facade | `IncidentService`, `AuthService` | Gives UI a small application-oriented API |
| Adapter | `RestClient`, `AuthClient`, `OpenStreetMapPanel` | Converts external protocols into application objects/UI |
| RBAC policy | `AccessPolicy` and database policies | Aligns visible capabilities with authoritative authorization |

### SOLID-style explanation

- **Single responsibility**: UI, services, models, transport, and security
  concerns are separated.
- **Dependency inversion**: `IncidentService` depends on the
  `IncidentRepository` interface rather than PostgREST.
- **Open/closed**: repository implementations and alarm strategies can evolve
  without rewriting the UI workflow.
- **Immutability**: records and copied timelines reduce cross-thread state bugs.

---

## 10. Testing and evidence

The repository includes JUnit tests for:

- incident builder required fields, coordinates, descriptions;
- incident lifecycle and transition blockers;
- role parsing and operational model integrity;
- strict module visibility for admin, responder, and guest;
- privileged-key rejection;
- alarm singleton, muting, deduplication, and eligibility;
- approximate geolocation validation/search behavior.

Manual verification scenarios are documented in
`TESTING.md` and cover registration, promotion, GIS, publication, alarms,
dispatch conflict prevention, status advancement, location sharing, resource
allocation/release, evacuation centers, SITREPs, and communication.

### Current validation note

At the time this guide was prepared, `mvn test` could not be executed in the
available environment because the `mvn` command was not installed. This is an
environment limitation, not a reported test failure. For the defense machine,
install JDK 25 and Maven 3.9+, configure Supabase, then run `mvn test` and
`mvn package` before the presentation.

---

## 11. Setup and deployment

### Prerequisites

- JDK 25 or later.
- Maven 3.9+.
- Supabase project.
- Network access for Supabase and map/geocoding services.

### Database setup

Run migrations `001` through `013` in order in the Supabase SQL Editor.
Bootstrap the first admin by creating an Auth user, then setting the matching
`profiles.role` to `admin` using the instructions in `SETUP.md`.

### Client setup

Create `GERMS/application.properties`:

```properties
supabase.url=https://<project-ref>.supabase.co
supabase.anonKey=<public-anon-or-publishable-key>
```

Never put a service-role key or secret key in this file.

### Run/build

```bash
mvn test
mvn exec:java
mvn package
java -jar target/germs-0.1.0-SNAPSHOT.jar
```

The Maven Shade plugin creates a runnable JAR with the configured main class
`com.gudo.germs.Main`.

---

## 12. Recommended defense demonstration order

1. State the problem and identify the three roles.
2. Show login/guest mode and explain Auth/JWT.
3. Submit a guest report.
4. Switch to admin and verify/publish it.
5. Show the incident lifecycle and the team-required mobilizing rule.
6. Open Dispatch and demonstrate responder response/assignment conflict
   prevention.
7. Open the map and explain marker colors and opt-in responder locations.
8. Show resource allocation and evacuation-center occupancy.
9. Show a SITREP and incident communication thread.
10. Trigger a test alarm, acknowledge it, and explain deduplication.
11. Show audit logs and analytics/CSV export.
12. End with the security architecture and limitations.

Always prepare an offline fallback: screenshots or seeded records for the
dashboard, incident list, map, dispatch, resources, SITREP, communications,
and audit log in case the live network/Supabase project is unavailable.

---

## 13. Questions the panel may ask

### Why Java Swing instead of a web frontend?

GERMS is intended as an operations desktop application. Swing provides a
stable desktop client, local audio capability, and a single deployable Java
application. The backend remains remote and centralized, so multiple command
and responder clients can share the same authoritative data.

### Why Supabase?

It provides managed Auth, PostgreSQL, REST access, RLS, and SQL functions
without requiring a separate custom API server. This reduces backend
boilerplate while keeping authorization and transactions inside PostgreSQL.

### Why are RPCs needed if PostgREST already exists?

Simple reads can use PostgREST. Privileged multi-step writes need an atomic,
server-controlled operation: authorization, state transition, notification,
resource update, and audit entry must not be reimplemented or trusted in the
client. RPCs provide that boundary.

### Why is RLS important?

The desktop client is untrusted. RLS protects data even if someone bypasses
the intended UI and sends direct HTTP requests with their own JWT.

### How is concurrency handled?

UI network calls run in background workers; model snapshots are immutable;
`IncidentService` serializes its main operations; database RPCs and row locks
protect privileged changes such as dispatch and resource allocation.

### How do you prevent duplicate alarms?

`AlarmService` stores notification IDs already played, stops the current line
before a new alarm, checks mute/preference/active-status conditions, and
acknowledgement prevents continued alerting.

### What happens if the network fails?

`SupabaseHttp` reports a `SupabaseException`, and the UI presents the failure
instead of claiming success. The system is not designed to invent local
authoritative data when the backend is unreachable.

### What is the most important limitation?

The current system is a connected desktop client: it requires a reachable
Supabase instance and network-backed map/geocoding services. It also uses
opt-in point location updates rather than continuous GPS tracking.

### How would you improve it next?

- Add a formal migration runner/CI pipeline and database integration tests.
- Add explicit realtime subscriptions or push delivery instead of polling.
- Add offline read-only caching and a conflict-aware synchronization strategy.
- Add stronger automated end-to-end tests against an isolated Supabase project.
- Add monitoring, deployment packaging, and backup/disaster-recovery
  procedures.
- Integrate approved SMS/radio/notification gateways if operationally needed.

---

## 14. Facts to memorize

- GERMS = Gudo Emergency Response Management System.
- Java 25, Maven, Swing, FlatLaf, Jackson, JUnit 5.
- Supabase Auth + PostgreSQL + PostgREST + RLS + secured RPCs.
- `AppContext` composes shared dependencies.
- `Shell` is role-filtered; database authorization is authoritative.
- Incident flow:
  `draft -> published -> acknowledged -> mobilizing -> active_response ->
  stabilized -> closed -> archived`.
- Mobilizing requires an assigned team.
- Assignment flow:
  `assigned -> preparing -> en_route -> arrived -> on_task -> completed`.
- Public reports enter a verification queue.
- Resources allocate atomically; audit logs are append-only.
- Responder location sharing is opt-in.
- The client uses only the public anon/publishable key.
- There are 13 migrations; migration 013 controls responder deployment-alert
  preferences and broadcast requests.
- Current alert refresh is five-second polling in `Shell`.
- The guide and existing `TESTING.md` distinguish automated unit tests from
  live-Supabase manual verification.

---

## 15. Source map for deeper study

- Product behavior: `README.md`
- Layer architecture: `ARCHITECTURE.md`
- Database entities/security: `DATABASE.md`
- Design patterns: `DESIGN_PATTERNS.md`
- Installation: `SETUP.md`
- Test scenarios: `TESTING.md`
- Implementation status: `docs/implementation-status.md`
- Build/dependencies: `pom.xml`
- Composition root: `src/main/java/com/gudo/germs/config/AppContext.java`
- Client transport: `src/main/java/com/gudo/germs/supabase/`
- Incident domain/service: `src/main/java/com/gudo/germs/models/` and
  `src/main/java/com/gudo/germs/services/`
- Role/module policy: `src/main/java/com/gudo/germs/security/`
- Database source of truth: `database/migrations/001_core_schema.sql` through
  `013_responder_alert_preferences.sql`

