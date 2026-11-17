# GERMS Developer and User Guide

**Source of truth:** this guide was checked against the Java sources, SQL
migrations, Supabase function, and project documentation in this repository.
It describes the checked-in implementation; database-backed behavior still
depends on the migrations being applied to the configured Supabase project.

## What GERMS does

GERMS (Gudo Emergency Response Management System) is a Java 25 Swing desktop
application for public incident reporting, emergency operations, and responder
coordination. Supabase Auth and PostgreSQL are authoritative for identity,
operational data, and access control. The client uses HTTPS/PostgREST and
secured database RPCs; it does not connect directly to PostgreSQL.

## End-to-end architecture and workflow

```text
 Public / Guest                 Responder                     Administrator
 register or guest mode         sign in; view alerts          sign in; command
 submit preliminary report      accept/decline response        verify & publish
 see own report status          assignment / SITREP / chat     dispatch & manage
          \                         |                               /
           +------------------------+------------------------------+
                                    v
                         GERMS Swing desktop client
       MainWindow -> role-filtered Shell -> lazy Swing module panels
       BackgroundTask / SwingWorker keeps network work off the UI thread
       IncidentService + AuthService coordinate application behavior
       IncidentBus posts incident change events back to Swing EDT
             |                                      |
             | model/repository interfaces         | map/geocoding
             v                                      v
       SupabaseIncidentRepository             OpenStreetMapPanel
       ProfileRepository                      tile server + local cache
             |                                 GeoLocationService
       RestClient / AuthClient                 Nominatim geocoding
       SupabaseHttp (HTTPS, anon key + user JWT) ipapi.co approximate IP lookup
             |
             +---- Supabase Auth (sign-up/sign-in/refresh/sign-out)
             +---- PostgREST table/view reads and permitted inserts/updates
             +---- PostgREST RPC endpoint
                         |
                         v
           Supabase PostgreSQL (system of record)
           RLS + role/ownership policies + SECURITY DEFINER RPC checks
           constraints, transactions, audit/history, notifications
```

### Request and event path

1. A panel accepts user input and invokes a service or repository operation.
   Network calls are normally wrapped in `BackgroundTask` so completion/error
   callbacks can safely update Swing controls.
2. `AuthClient` talks to Supabase Auth. `SessionManager` holds the access and
   refresh tokens in memory and refreshes an expiring access token before a
   protected request.
3. `RestClient` sends PostgREST reads, allowed row operations, RPCs, and
   optional Edge Function calls through `SupabaseHttp`. The public project key
   identifies the client; the signed-in user's JWT determines the database
   identity. PostgreSQL RLS and RPC checks are the security boundary.
4. Repository code maps JSON to domain types. `IncidentService` coordinates
   incident commands and publishes `IncidentEvent`s through `IncidentBus`,
   which delivers listeners on Swing's Event Dispatch Thread.
5. Map tiles and geocoding are external HTTP services, not Supabase data.
   Incident and responder location data is loaded separately and drawn as
   markers on the desktop-rendered map.

### Role flows

**Guest/public user**

1. Start in guest mode or register. Auth signup creates a guest profile through
   the database signup trigger; signing in restores the user's profile.
2. Submit a preliminary incident report. It is stored as a `guest_reports`
   record in `unverified` state; it is not automatically a published incident.
3. Review submitted-report status, public announcements/advisories, public
   evacuation-center information, and emergency contacts where current RLS
   and publication flags permit.

**Responder**

1. An administrator provisions/promotes the account; a responder signs in and
   has an active responder profile and availability.
2. View incidents/notifications, respond to a deployment request by accepting
   or declining, and wait for command to confirm an assignment.
3. Progress an assignment through `assigned -> preparing -> en_route ->
   arrived -> on_task -> completed` (or be released/cancelled by command).
4. Optionally share an operational location, submit field SITREPs, and use an
   incident communication channel. Sharing is opt-in and does not provide
   continuous GPS tracking.

**Administrator**

1. Verify/reject a public report; for a verified report create/publish an
   operational incident with category, severity, description, and selected
   location.
2. Set severity/status, assign a team, review responder decisions, confirm or
   cancel deployments, and resolve/close the operation.
3. Manage accounts and responder classifications, teams, equipment
   allocations, evacuation centers, announcements, communication, and
   notifications; inspect audit history and export analytics as CSV.

### Lifecycle and transition notes

The incident state sequence in the domain model is:

`draft -> published -> acknowledged -> mobilizing -> active_response -> stabilized -> closed -> archived`

`IncidentStatus.next()` describes the forward sequence and `blocker()` prevents
advancing to `mobilizing` until a response team is assigned. Database
`incident_status_transitions` is also seeded and enforced by privileged
operations. Do not treat the Swing role/module filter as permission to write.

Assignment states represented in the database are `assigned`, `preparing`,
`en_route`, `arrived`, `on_task`, `completed`, `released`, and `cancelled`.
`assignment_status_transitions` stores permitted transitions; responder UI
progression covers the active sequence, while command can release/cancel.

Guest report review is a separate lifecycle (`unverified`, `verified`,
`rejected`, `converted`); verification does not mean that an incident has
already been published.

## Folder map

Paths are relative to `GERMS/`.

| Path | Responsibility |
|---|---|
| `pom.xml` | Java 25 Maven build, dependencies, tests, executable shaded JAR. |
| `src/main/java/com/gudo/germs/Main.java` | Desktop entry point. |
| `.../config/` | Environment/property loading and shared application wiring. |
| `.../models/` | Incident, profile, role, team, timeline and lifecycle domain types. |
| `.../repositories/` | Profile persistence adapter. |
| `.../security/` | Role-to-module presentation policy; not the server security layer. |
| `.../services/` | Authentication, incident orchestration, geolocation, events and alarm audio. |
| `.../supabase/` | HTTP, Auth, PostgREST/RPC, session and exception adapters. |
| `.../ui/` | Main window, role-specific shell, panels and `RefreshablePanel`. |
| `.../ui/admin/` | Dashboard, dispatch, audit, teams, resources, shelters, map, reports and accounts. |
| `.../ui/auth/` | Login/registration and visual background components. |
| `.../ui/components/` | Shared tables, map, location picker, contact/announcement directories and widgets. |
| `.../ui/guest/` | Public report, submitted reports and settings. |
| `.../ui/incident/` | Incident feed, deployments, SITREP, team, notification and communication panels. |
| `.../ui/theme/` | Theme state and shared color palette. |
| `.../utils/` | Swing background-task helper and responder classification normalization. |
| `src/main/resources/` | Classpath properties fallback and logo image asset. |
| `src/test/java/` | JUnit tests for lifecycle/models, access policy, alarms, geolocation and classifications. |
| `database/migrations/` | Ordered PostgreSQL schema, policies, RPC, operational features and fixes (001–017). |
| `database/seed_data.sql` | First-admin bootstrap instructions and seed/bootstrap SQL. |
| `docs/` | Existing setup, architecture, database, testing, defense, design-pattern and status documents. |

Repository-level `supabase/functions/admin-create-user/index.ts` is an Edge
Function implementation, separate from the Java Maven project. No deployment
manifest/automation for that function is present here.

## Data and security model

### Main data groups

* **Identity:** `auth.users` owns credentials; `profiles` stores GERMS role,
  name, contact, active status, and classification links. Signup trigger
  creates a guest profile.
* **Incidents:** `incidents` is the current operational snapshot;
  `incident_updates` and audit rows preserve event history. `guest_reports`
  stores unverified public submissions separately.
* **Response:** `incident_responses` records accept/decline; assignments and
  `assignment_status_history` record confirmed deployment and progress.
  `response_teams` / `team_members` describe squads.
* **Operations:** `resources` and `resource_allocations`, evacuation centers,
  responder availability/locations, SITREPs, incident messages, announcements,
  emergency contacts, notifications and receipts support field operations.
* **Reference/transition data:** categories, role/status enums, service
  divisions, classification codes, and incident/assignment transition rows.

### Authorization controls

* Desktop configuration accepts only the public anon/publishable key; startup
  rejects `sb_secret_...` and service-role JWT credentials.
* Authenticated requests carry the user's bearer JWT. RLS policies enforce row
  visibility and ownership, and privileged actions use database RPCs with
  server-side role/assignment checks. A user bypassing the UI remains subject
  to these controls.
* UI `AccessPolicy` determines which modules appear for Admin, Responder, and
  Guest; it is navigation only, not authorization.
* Important writes (incident commands, assignment, role management, resource
  allocation, location, SITREP, messaging and notification operations) use
  server-side functions as applicable. Constraints, locking/atomic database
  changes, foreign keys and history tables protect consistency.
* `audit_logs` has an update/delete-blocking trigger and is readable by
  administrators under policy. RPCs record privileged operations.
* The Edge Function uses a service-role key only in its server environment,
  authenticates its caller and checks active admin status before creating an
  Auth user. Never put that server secret in desktop configuration.

## SQL migrations and database routines

Run every migration **once, in numeric order**, against a backed-up Supabase
project. The checked-in `DATABASE.md` is the detailed entity/policy reference.

| Migration | Purpose / SQL area |
|---|---|
| `001_core_schema.sql` | Enums, core identity/incident/response/notification/audit/guest tables, timestamp trigger, append-only audit trigger. |
| `002_rls_policies.sql` | RLS, `is_admin`, `is_responder`, `can_view_incident`, public advisory view and row policies. |
| `003_rpc_functions.sql` | Audit/admin helpers; incident create/update/publish/status/update, response/assignment, notification and response-summary RPCs. |
| `004_reference_seed.sql` | Reference values and legal incident/assignment transition rows. |
| `005_response_teams.sql` | Response teams, incident team relation and team-assignment RPC. |
| `006_operations_resources.sql` | Evacuation centers and legacy disaster-resource table with policies/triggers; unused legacy resource table is later dropped. |
| `007_admin_management.sql` | Admin-checked profile update/deactivation RPCs. |
| `008_responder_command.sql` | Responder availability and deployment-request RPCs. |
| `009_phase3_operations.sql` | Guest signup/profile trigger, teams, resources/allocations, SITREPs, messages, contacts/announcements, location and related policies/RPCs. |
| `010_fix_auth_registration_search_path.sql` | Fixes signup trigger search path and type resolution. |
| `011_fix_profile_relationships.sql` | Corrects profile relationships for PostgREST embedding/audit actors. |
| `012_incident_severity_updates.sql` | Secured incident-severity update operation. |
| `013_responder_alert_preferences.sql` | Responder deployment-alert preferences and broadcast deployment requests. |
| `014_admin_notification_center.sql` | Separate admin acknowledgement/receipt for shared notifications. |
| `015_responder_self_classification.sql` | **Empty in this checkout (0 bytes); applying it executes no SQL.** |
| `016_drop_unused_tables.sql` | Explicitly removes unused legacy tables using dependency-safe `RESTRICT`; back up first. |
| `017_admin_responder_classifications.sql` | Admin classification editing and incident-scoped message sender directory. |

The classification self-service/admin RPCs and classification seed data are in
`017_admin_responder_classifications.sql`; older `DATABASE.md` text attributes
some of this to 015, but the checked-in 015 file is empty. The principal
triggers are `trg_profiles_touch`, `trg_incidents_touch`,
`trg_evacuation_centers_touch`, `trg_audit_immutable`, and
`on_auth_user_created` (profile creation from `auth.users`). The shared
`private.touch_updated_at()` trigger function updates timestamps;
`private.block_audit_mutation()` rejects audit update/delete. Migrations 002
define `public.is_admin()`, `public.is_responder()`, and
`public.can_view_incident(uuid)` policy helpers.

Important RPC groups (the SQL files remain the authoritative full
definitions):

* **Incident/response (003):** `create_incident`, `update_incident`,
  `publish_incident`, `set_incident_status`, `post_incident_update`,
  `respond_to_incident`, `assign_responder`, `update_assignment_status`,
  `mark_notification_observed`, `acknowledge_notification`, and
  `incident_response_summary`; the shared `private.audit(...)` and
  `private.require_admin()` helpers support authorization/auditing.
* **Teams/admin/availability (005, 007–008):** `assign_incident_team`,
  `update_profile_admin`, `deactivate_profile_admin`,
  `admin_set_responder_availability`, and `request_responder_deployment`.
* **Operations (009):** `promote_to_responder`, `allocate_resource`,
  `release_resource`, `submit_field_situation_report`,
  `send_incident_message`, and `update_responder_location`.
* **Later operations (012–017):** `update_incident_severity`,
  `set_deployment_notification_preference`,
  `request_all_active_responder_deployments`,
  `admin_acknowledge_notification`,
  `set_own_responder_classification`,
  `update_profile_admin_with_classification`, and
  `incident_message_sender_directory`.

The migration RPCs cover authorization-sensitive workflows rather than
exposing direct database credentials. Migration 016 removes
`disaster_resources`, `notification_preferences`, `incident_command_assignments`,
`responder_qualifications`, `incident_command_positions`, and `certifications`;
do not infer they remain active just because earlier migrations create them.

## Run and configure

### Prerequisites and Supabase setup

Use JDK 25+ and Maven 3.9+ (versions documented in `SETUP.md`). Create a
Supabase project, run migrations `database/migrations/001` through `017` in
order, create the first Auth user, and promote that user's profile to `admin`
using the bootstrap steps in `SETUP.md` / `database/seed_data.sql`. Back up
before applying migration 016.

### Client configuration

Provide `supabase.url` and `supabase.anonKey` in `application.properties`, or
set `GERMS_SUPABASE_URL` and `GERMS_SUPABASE_ANON_KEY`. The implementation
checks environment variables first, then `./application.properties`, then
classpath `application.properties`; environment values override the file.
Use only a public anon/publishable key. Do not copy a project-specific key into
documentation, source, or shared artifacts. For a packaged JAR, either provide
the working-directory properties file or package an appropriate classpath
configuration; run from the folder containing local `application.properties`
when using the former.

### Build, run and package

From `GERMS/`:

```powershell
mvn test
mvn exec:java
mvn package
java -jar target/germs-0.1.0-SNAPSHOT.jar
```

`pom.xml` sets `com.gudo.germs.Main` as the executable main class and shades
runtime dependencies into the JAR. See `README.md` and `SETUP.md` for the
operator sequence and bootstrap SQL.

### Optional user provisioning function

`supabase/functions/admin-create-user/index.ts` validates request fields and
roles, authenticates the bearer token, checks the caller's active admin
profile, creates the Auth user, then inserts the profile (rolling back the
Auth user if profile insertion fails). Deploy/configure it separately with
`SUPABASE_URL`, `SUPABASE_ANON_KEY`, and `SUPABASE_SERVICE_ROLE_KEY` as
function secrets; its service-role key belongs only in the trusted function
runtime. The repository does not include deployment instructions or automated
deployment for this function.

## Maps, geocoding and location

`OpenStreetMapPanel` renders slippy-map tiles in Swing, projects
latitude/longitude to tile pixels, supports pan/zoom/click-to-pin, and caches
tiles in memory and `.germs_osm_tiles`. `GeoLocationService` validates
coordinates, searches and reverse-geocodes using the public Nominatim service,
and can request approximate network-IP location from ipapi.co. The latter is
approximate and externally processed; explicit consent is required by the
responder flow. Geocoding and tiles require network access and remain subject
to the external providers' policies, availability and usage limits.

## Tests and verification

Run `mvn test`. Six checked-in test classes cover:

* `IncidentLifecycleTest` — builder validations, immutable model behavior,
  status progression/blocker and legacy status parsing.
* `OperationalModelTest` — profile/role JSON mapping and response-team model.
* `AccessPolicyTest` — module visibility for roles and privileged-key rejection.
* `AlarmServiceTest` — singleton/muting, alarm deduplication and responder
  preference/activity filtering.
* `GeoLocationServiceTest` — coordinate validation and geolocation behavior.
* `ResponderClassificationCodesTest` — classification code normalization.

These are unit tests, not a live-Supabase integration suite. Feature paths
requiring current schema, RLS, RPCs, Auth, external map/geocoding endpoints, or
audio hardware must also be verified in the target environment; manual
scenarios are in `TESTING.md`. This environment had no Maven command available,
so no fresh Maven test/build result is claimed by this guide.

## Related repository documentation

* [README](../README.md) — quick start and role workflows.
* [SETUP](../SETUP.md) — prerequisites, migration sequence and first-admin bootstrap.
* [ARCHITECTURE](../ARCHITECTURE.md) — subsystem and GIS/audio architecture.
* [DATABASE](../DATABASE.md) — entity relationships, RLS and RPC details.
* [TESTING](../TESTING.md) — unit-test scope and manual operating scenarios.
* [DESIGN_PATTERNS](../DESIGN_PATTERNS.md) — patterns used in the client.
* [DEFENSE_GUIDE](../DEFENSE_GUIDE.md) — demonstration and project discussion notes.
* [Implementation status](implementation-status.md) — existing feature-status summary.

## Known limitations and operational cautions

* Shell notification polling is periodic (the database guide documents a
  five-second check); the checked-in client does not use Supabase Realtime.
* Responder location is optional, explicitly shared, and stored as a latest
  operational point, not continuous GPS history. Approximate IP geolocation is
  not an operationally precise position.
* The map depends on external OSM tiles and geocoding services; cached tiles
  do not make geocoding or initial map loads fully offline.
* The client is a desktop Swing application; there is no checked-in mobile
  client or deployment automation for the Edge Function.
* Module visibility is client-side convenience; database RLS and RPC
  authorization must remain correctly migrated and configured.
* The current database must be migrated through 017. Older docs/status notes
  that stop at 013 are historical and are superseded by `SETUP.md` and
  `DATABASE.md`.

## Complete Java source inventory

The following inventory was extracted from the Java compiler's syntax trees
for every `.java` file under `src/main/java` and `src/test/java`, not by
truncating source with a regular expression. It includes nested, local and anonymous class
declarations. Method entries name the operation, give a short purpose based on
the containing type and implementation's opening statement, and point to the
source line. Constructors are explicitly identified; method overloads are
separate entries. Compiler-generated record accessors are not declared source
methods and are therefore not counted.

**Inventory validation:** exact file/type/method totals are printed in the
AST count below; per-file and per-type member counts are included below so the
inventory can be checked against the source tree.

<!-- JAVA_INVENTORY_START -->

**AST count check:** 69 Java source files; 161 declared types (113 named, 48 anonymous); 673 explicit constructors/methods (612 in named types, 61 in anonymous types).

#### Folder `src/main/java/com/gudo/germs/`

##### `src/main/java/com/gudo/germs/Main.java`

**class `Main`** (line 9) — Named class supporting main behavior in its containing package. 2 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Provides an intentionally empty <init> implementation. | 10 |
| `main` | Starts the desktop application; the implementation begins by calling invoke later. | 12 |


#### Folder `src/main/java/com/gudo/germs/config/`

##### `src/main/java/com/gudo/germs/config/AppConfig.java`

**class `AppConfig`** (line 12) — Named class supporting app config behavior in its containing package. 7 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the app config type; the implementation stores the updated value in `this.supabaseUrl`. | 16 |
| `load` | Loads ; the implementation prepares `props` before completing the operation. | 21 |
| `isConfigured` | Reports whether configured; the implementation evaluates and returns the boolean condition. | 43 |
| `supabaseUrl` | Returns the result for supabase url; the implementation returns the `supabaseUrl` field/value. | 47 |
| `anonKey` | Returns the result for anon key; the implementation returns the `anonKey` field/value. | 48 |
| `isPrivilegedKey` | Reports whether privileged key; the implementation checks a guard condition before continuing. | 51 |
| `firstNonBlank` | Performs the first non blank operation for app config; the implementation checks a guard condition before continuing. | 64 |

##### `src/main/java/com/gudo/germs/config/AppContext.java`

**class `AppContext`** (line 13) — Named class supporting app context behavior in its containing package. 3 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the app context type; the implementation stores the updated value in `this.config`. | 22 |
| `get` | Returns the  value; the implementation checks a guard condition before continuing. | 31 |
| `create` | Returns the result for create; the implementation computes and returns the result of its checks or mapping. | 37 |


#### Folder `src/main/java/com/gudo/germs/models/`

##### `src/main/java/com/gudo/germs/models/Incident.java`

**record `Incident`** (line 8) — Immutable data/value type for incident information. 10 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the incident type; the implementation stores the updated value in `timeline`. | 12 |
| `constructor` | Initializes the incident type; the implementation begins by calling this. | 17 |
| `reference` | Returns the result for reference; the implementation computes and returns the result of its checks or mapping. | 23 |
| `toString` | Formats a display string for incident; the implementation computes and returns the result of its checks or mapping. | 27 |
| `withIncidentNo` | Returns the result for with incident no; the implementation computes and returns the result of its checks or mapping. | 29 |
| `withId` | Returns the result for with id; the implementation computes and returns the result of its checks or mapping. | 33 |
| `withStatus` | Returns the result for with status; the implementation computes and returns the result of its checks or mapping. | 37 |
| `withTeam` | Returns the result for with team; the implementation computes and returns the result of its checks or mapping. | 41 |
| `withCoordinates` | Returns the result for with coordinates; the implementation computes and returns the result of its checks or mapping. | 45 |
| `plus` | Performs the plus operation for incident; the implementation prepares `copy` before completing the operation. | 49 |

##### `src/main/java/com/gudo/germs/models/IncidentBuilder.java`

**class `IncidentBuilder`** (line 7) — Builder that collects and validates incident builder data. 15 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `id` | Performs the id operation for incident builder; the implementation stores the updated value in `this.id`. | 23 |
| `incidentNo` | Performs the incident no operation for incident builder; the implementation stores the updated value in `this.incidentNo`. | 24 |
| `title` | Performs the title operation for incident builder; the implementation stores the updated value in `this.title`. | 25 |
| `type` | Performs the type operation for incident builder; the implementation stores the updated value in `this.type`. | 26 |
| `location` | Performs the location operation for incident builder; the implementation stores the updated value in `this.location`. | 27 |
| `severity` | Performs the severity operation for incident builder; the implementation stores the updated value in `this.severity`. | 28 |
| `status` | Performs the status operation for incident builder; the implementation stores the updated value in `this.status`. | 29 |
| `team` | Performs the team operation for incident builder; the implementation stores the updated value in `this.team`. | 30 |
| `reportedBy` | Performs the reported by operation for incident builder; the implementation stores the updated value in `this.reportedBy`. | 31 |
| `reportedAt` | Performs the reported at operation for incident builder; the implementation stores the updated value in `this.reportedAt`. | 32 |
| `latitude` | Performs the latitude operation for incident builder; the implementation stores the updated value in `this.latitude`. | 33 |
| `longitude` | Performs the longitude operation for incident builder; the implementation stores the updated value in `this.longitude`. | 34 |
| `description` | Performs the description operation for incident builder; the implementation stores the updated value in `this.description`. | 35 |
| `note` | Performs the note operation for incident builder; the implementation begins by calling add. | 38 |
| `build` | Builds ; the implementation checks a guard condition before continuing. | 43 |

##### `src/main/java/com/gudo/germs/models/IncidentStatus.java`

**enum `IncidentStatus`** (line 9) — Finite set of incident status values used by the application. 8 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the incident status type; the implementation stores the updated value in `this.key`. | 26 |
| `key` | Returns the result for key; the implementation returns the `key` field/value. | 28 |
| `label` | Returns the result for label; the implementation returns the `label` field/value. | 29 |
| `fromKey` | Parses or maps from key data; the implementation checks a guard condition before continuing. | 31 |
| `next` | Selects the next lifecycle value for incident status; the implementation selects and returns a value based on the current state. | 48 |
| `blocker` | Returns the result for blocker; the implementation computes and returns the result of its checks or mapping. | 61 |
| `isActive` | Reports whether active; the implementation computes and returns the result of its checks or mapping. | 64 |
| `isTerminal` | Reports whether terminal; the implementation computes and returns the result of its checks or mapping. | 69 |

**enum `<anonymous@13>`** (line 13) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `blocker` | Returns the result for blocker; the implementation computes and returns the result of its checks or mapping. | 14 |

##### `src/main/java/com/gudo/germs/models/Profile.java`

**record `Profile`** (line 7) — Immutable data/value type for profile information. 2 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `fromJson` | Returns the result for from json; the implementation computes and returns the result of its checks or mapping. | 8 |
| `guest` | Returns the result for guest; the implementation computes and returns the result of its checks or mapping. | 13 |

##### `src/main/java/com/gudo/germs/models/ResponseTeam.java`

**record `ResponseTeam`** (line 7) — Immutable data/value type for response team information. 2 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `fromJson` | Returns the result for from json; the implementation computes and returns the result of its checks or mapping. | 8 |
| `toString` | Formats a display string for response team; the implementation returns the `name` field/value. | 12 |

##### `src/main/java/com/gudo/germs/models/Role.java`

**enum `Role`** (line 3) — Finite set of role values used by the application. 3 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the role type; the implementation stores the updated value in `this.label`. | 7 |
| `label` | Returns the result for label; the implementation returns the `label` field/value. | 8 |
| `fromDb` | Parses or maps from db data; the implementation checks a guard condition before continuing. | 10 |

##### `src/main/java/com/gudo/germs/models/TimelineEntry.java`

**record `TimelineEntry`** (line 6) — Immutable data/value type for timeline entry information. 0 explicitly declared constructor/method member(s).


#### Folder `src/main/java/com/gudo/germs/repositories/`

##### `src/main/java/com/gudo/germs/repositories/ProfileRepository.java`

**class `ProfileRepository`** (line 11) — Data access contract/adapter for profile repository records. 3 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the profile repository type; the implementation stores the updated value in `this.rest`. | 14 |
| `findById` | Looks up by id; the implementation prepares `rows` before completing the operation. | 16 |
| `ensureGuestProfile` | Performs the ensure guest profile operation for profile repository; the implementation runs its operation with guarded error handling. | 21 |


#### Folder `src/main/java/com/gudo/germs/security/`

##### `src/main/java/com/gudo/germs/security/AccessPolicy.java`

**class `AccessPolicy`** (line 9) — Named class supporting access policy behavior in its containing package. 2 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Provides an intentionally empty <init> implementation. | 10 |
| `modulesFor` | Returns the modules permitted for the supplied role; the implementation computes and returns the result of its checks or mapping. | 12 |

##### `src/main/java/com/gudo/germs/security/Module.java`

**enum `Module`** (line 12) — Finite set of module values used by the application. 5 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the module type; the implementation stores the updated value in `this.title`. | 46 |
| `title` | Returns the result for title; the implementation returns the `title` field/value. | 53 |
| `phase` | Returns the result for phase; the implementation returns the `phase` field/value. | 54 |
| `description` | Returns the result for description; the implementation returns the `description` field/value. | 55 |
| `allows` | Returns the result for allows; the implementation computes and returns the result of its checks or mapping. | 56 |


#### Folder `src/main/java/com/gudo/germs/services/`

##### `src/main/java/com/gudo/germs/services/AlarmService.java`

**class `AlarmService`** (line 22) — Application service for alarm service behavior. 13 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Provides an intentionally empty <init> implementation. | 38 |
| `get` | Returns the  value; the implementation checks a guard condition before continuing. | 40 |
| `isMuted` | Reports whether muted; the implementation computes and returns the result of its checks or mapping. | 47 |
| `setMuted` | Sets muted; the implementation begins by calling set. | 51 |
| `isPlaying` | Reports whether playing; the implementation computes and returns the result of its checks or mapping. | 58 |
| `stopAlarm` | Stops alarm; the implementation begins by calling set. | 65 |
| `triggerAlarm` | Performs the trigger alarm operation for alarm service; the implementation checks a guard condition before continuing. | 81 |
| `playLowChime` | Generates or plays low chime; the implementation prepares `p` before completing the operation. | 114 |
| `playModerateAlert` | Generates or plays moderate alert; the implementation prepares `tone1` before completing the operation. | 120 |
| `playHighAlarm` | Generates or plays high alarm; the implementation prepares `deadline` before completing the operation. | 133 |
| `playCriticalAlarm` | Generates or plays critical alarm; the implementation prepares `deadline` before completing the operation. | 144 |
| `generateTone` | Performs the generate tone operation for alarm service; the implementation prepares `sampleRate` before completing the operation. | 165 |
| `playRawPcm` | Generates or plays raw pcm; the implementation checks a guard condition before continuing. | 186 |

##### `src/main/java/com/gudo/germs/services/AuthService.java`

**class `AuthService`** (line 11) — Application service for auth service behavior. 5 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the auth service type; the implementation stores the updated value in `this.authClient`. | 17 |
| `login` | Performs the login operation for auth service; the implementation checks a guard condition before continuing. | 24 |
| `register` | Performs the register operation for auth service; the implementation checks a guard condition before continuing. | 43 |
| `logout` | Performs the logout operation for auth service; the implementation prepares `token` before completing the operation. | 61 |
| `currentProfile` | Returns the result for current profile; the implementation computes and returns the result of its checks or mapping. | 70 |

##### `src/main/java/com/gudo/germs/services/GeoLocationService.java`

**class `GeoLocationService`** (line 20) — Application service for geo location service behavior. 5 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Provides an intentionally empty <init> implementation. | 53 |
| `detectApproximateLocationByIp` | Performs the detect approximate location by ip operation for geo location service; the implementation prepares `uri` before completing the operation. | 59 |
| `validCoordinates` | Reports whether coordinates; the implementation computes and returns the result of its checks or mapping. | 89 |
| `search` | Performs the search operation for geo location service; the implementation checks a guard condition before continuing. | 98 |
| `reverseGeocode` | Performs the reverse geocode operation for geo location service; the implementation checks a guard condition before continuing. | 159 |

**record `GeoPoint`** (line 24) — Immutable data/value type for geo point information. 0 explicitly declared constructor/method member(s).

**record `SearchResult`** (line 25) — Immutable data/value type for search result information. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `toString` | Formats a display string for search result; the implementation returns the `displayName` field/value. | 26 |

**record `SearchResponse`** (line 28) — Immutable data/value type for search response information. 0 explicitly declared constructor/method member(s).

##### `src/main/java/com/gudo/germs/services/IncidentBus.java`

**class `IncidentBus`** (line 13) — Named class supporting incident bus behavior in its containing package. 2 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `subscribe` | Registers a listener for ; the implementation begins by calling add. | 17 |
| `publish` | Publishes or broadcasts ; the implementation begins by calling invoke later. | 22 |

##### `src/main/java/com/gudo/germs/services/IncidentEvent.java`

**record `IncidentEvent`** (line 5) — Immutable data/value type for incident event information. 0 explicitly declared constructor/method member(s).

**enum `Type`** (line 6) — Finite set of type values used by the application. 0 explicitly declared constructor/method member(s).

##### `src/main/java/com/gudo/germs/services/IncidentRepository.java`

**interface `IncidentRepository`** (line 12) — Contract for incident repository behavior. 10 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `findAll` | Defines the find all contract for this type (abstract/interface declaration). | 13 |
| `findPublic` | Defines the find public contract for this type (abstract/interface declaration). | 14 |
| `find` | Defines the find contract for this type (abstract/interface declaration). | 15 |
| `findTeams` | Defines the find teams contract for this type (abstract/interface declaration). | 16 |
| `create` | Defines the create contract for this type (abstract/interface declaration). | 17 |
| `submitGuestReport` | Defines the submit guest report contract for this type (abstract/interface declaration). | 18 |
| `updateSeverity` | Defines the update severity contract for this type (abstract/interface declaration). | 19 |
| `assignTeam` | Defines the assign team contract for this type (abstract/interface declaration). | 20 |
| `advance` | Defines the advance contract for this type (abstract/interface declaration). | 21 |
| `respond` | Defines the respond contract for this type (abstract/interface declaration). | 22 |

##### `src/main/java/com/gudo/germs/services/IncidentService.java`

**class `IncidentService`** (line 19) — Application service for incident service behavior. 13 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the incident service type; the implementation stores the updated value in `this.repo`. | 25 |
| `bus` | Returns the result for bus; the implementation returns the `bus` field/value. | 27 |
| `visibleTo` | Returns the result for visible to; the implementation computes and returns the result of its checks or mapping. | 30 |
| `responseTeams` | Returns the result for response teams; the implementation computes and returns the result of its checks or mapping. | 34 |
| `updateSeverity` | Updates severity; the implementation checks a guard condition before continuing. | 38 |
| `report` | Returns the result for report; the implementation computes and returns the result of its checks or mapping. | 52 |
| `report` | Performs the report operation for incident service; the implementation prepares `built` before completing the operation. | 57 |
| `submitGuestReport` | Returns the result for submit guest report; the implementation computes and returns the result of its checks or mapping. | 72 |
| `submitGuestReport` | Submits guest report; the implementation prepares `report` before completing the operation. | 77 |
| `assignTeam` | Performs the assign team operation for incident service; the implementation prepares `cur` before completing the operation. | 93 |
| `advance` | Validates and advances advance; the implementation prepares `cur` before completing the operation. | 101 |
| `respond` | Records or processes respond; the implementation checks a guard condition before continuing. | 121 |
| `get` | Returns the result for get; the implementation computes and returns the result of its checks or mapping. | 133 |

##### `src/main/java/com/gudo/germs/services/SupabaseIncidentRepository.java`

**class `SupabaseIncidentRepository`** (line 20) — Data access contract/adapter for supabase incident repository records. 15 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the supabase incident repository type; the implementation stores the updated value in `this.rest`. | 29 |
| `findAll` | Looks up all; the implementation prepares `rows` before completing the operation. | 31 |
| `findPublic` | Looks up public; the implementation prepares `rows` before completing the operation. | 41 |
| `find` | Looks up ; the implementation prepares `uuid` before completing the operation. | 72 |
| `findTeams` | Looks up teams; the implementation prepares `rows` before completing the operation. | 78 |
| `create` | Creates ; the implementation prepares `values` before completing the operation. | 87 |
| `submitGuestReport` | Submits guest report; the implementation prepares `description` before completing the operation. | 103 |
| `updateSeverity` | Updates severity; the implementation begins by calling rpc. | 125 |
| `assignTeam` | Performs the assign team operation for supabase incident repository; the implementation begins by calling rpc. | 132 |
| `advance` | Validates and advances advance; the implementation prepares `uuid` before completing the operation. | 139 |
| `respond` | Records or processes respond; the implementation begins by calling rpc. | 152 |
| `toIncident` | Converts to incident to its target representation; the implementation prepares `id` before completing the operation. | 157 |
| `nestedName` | Performs the nested name operation for supabase incident repository; the implementation checks a guard condition before continuing. | 207 |
| `categoryCode` | Performs the category code operation for supabase incident repository; the implementation prepares `normalized` before completing the operation. | 214 |
| `categoryLabel` | Returns the result for category label; the implementation selects and returns a value based on the current state. | 223 |


#### Folder `src/main/java/com/gudo/germs/supabase/`

##### `src/main/java/com/gudo/germs/supabase/AuthClient.java`

**class `AuthClient`** (line 9) — Transport adapter for auth client operations. 6 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the auth client type; the implementation stores the updated value in `this.http`. | 12 |
| `signIn` | Performs the sign in operation for auth client; the implementation prepares `body` before completing the operation. | 14 |
| `signUp` | Performs the sign up operation for auth client; the implementation prepares `dataNode` before completing the operation. | 19 |
| `refresh` | Refreshes ; the implementation prepares `body` before completing the operation. | 37 |
| `signOut` | Performs the sign out operation for auth client; the implementation begins by calling send. | 42 |
| `parse` | Parses or maps parse data; the implementation checks a guard condition before continuing. | 46 |

##### `src/main/java/com/gudo/germs/supabase/RestClient.java`

**class `RestClient`** (line 8) — Transport adapter for rest client operations. 6 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the rest client type; the implementation stores the updated value in `this.http`. | 12 |
| `select` | Returns the result for select; the implementation computes and returns the result of its checks or mapping. | 18 |
| `rpc` | Performs the rpc operation for rest client; the implementation prepares `body` before completing the operation. | 23 |
| `invokeFunction` | Performs the invoke function operation for rest client; the implementation prepares `body` before completing the operation. | 28 |
| `insertMinimal` | Performs the insert minimal operation for rest client; the implementation begins by calling send. | 34 |
| `patch` | Returns the result for patch; the implementation computes and returns the result of its checks or mapping. | 40 |

##### `src/main/java/com/gudo/germs/supabase/Session.java`

**record `Session`** (line 6) — Immutable data/value type for session information. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `expiresWithin` | Returns the result for expires within; the implementation computes and returns the result of its checks or mapping. | 7 |

##### `src/main/java/com/gudo/germs/supabase/SessionManager.java`

**class `SessionManager`** (line 6) — Shared state/lifecycle manager for session manager. 5 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the session manager type; the implementation stores the updated value in `this.auth`. | 10 |
| `set` | Sets ; the implementation stores the updated value in `this.session`. | 12 |
| `clear` | Performs the clear operation for session manager; the implementation stores the updated value in `this.session`. | 13 |
| `current` | Returns the result for current; the implementation computes and returns the result of its checks or mapping. | 14 |
| `accessToken` | Performs the access token operation for session manager; the implementation checks a guard condition before continuing. | 17 |

##### `src/main/java/com/gudo/germs/supabase/SupabaseException.java`

**class `SupabaseException`** (line 3) — Named class supporting supabase exception behavior in its containing package. 3 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the supabase exception type; the implementation begins by calling super. | 6 |
| `status` | Returns the result for status; the implementation returns the `status` field/value. | 12 |
| `isNetworkError` | Reports whether network error; the implementation computes and returns the result of its checks or mapping. | 13 |

##### `src/main/java/com/gudo/germs/supabase/SupabaseHttp.java`

**class `SupabaseHttp`** (line 16) — Named class supporting supabase http behavior in its containing package. 3 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the supabase http type; the implementation stores the updated value in `this.config`. | 22 |
| `send` | Performs the send operation for supabase http; the implementation checks a guard condition before continuing. | 26 |
| `extractMessage` | Performs the extract message operation for supabase http; the implementation runs its operation with guarded error handling. | 56 |


#### Folder `src/main/java/com/gudo/germs/ui/`

##### `src/main/java/com/gudo/germs/ui/MainWindow.java`

**class `MainWindow`** (line 22) — Named class supporting main window behavior in its containing package. 4 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the main window type; the implementation begins by calling super. | 32 |
| `showShell` | Displays shell; the implementation checks a guard condition before continuing. | 46 |
| `signOut` | Performs the sign out operation for main window; the implementation begins by calling run. | 56 |
| `showLogin` | Displays login; the implementation checks a guard condition before continuing. | 63 |

##### `src/main/java/com/gudo/germs/ui/RefreshablePanel.java`

**interface `RefreshablePanel`** (line 4) — Contract for refreshable panel behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `refreshData` | Defines the refresh data contract for this type (abstract/interface declaration). | 5 |

##### `src/main/java/com/gudo/germs/ui/Shell.java`

**class `Shell`** (line 80) — Named class supporting shell behavior in its containing package. 15 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the shell type; the implementation begins by calling super. | 98 |
| `addNotify` | Adds notify to managed state; the implementation begins by calling add notify. | 145 |
| `removeNotify` | Removes notify from managed state; the implementation begins by calling stop. | 156 |
| `checkEmergencyAlerts` | Checks emergency alerts; the implementation checks a guard condition before continuing. | 165 |
| `receiptAcknowledged` | Performs the receipt acknowledged operation for shell; the implementation checks a guard condition before continuing. | 233 |
| `showNotificationToast` | Displays notification toast; the implementation stores the updated value in `pendingNotificationId`. | 240 |
| `registerTrayIcon` | Performs the register tray icon operation for shell; the implementation checks a guard condition before continuing. | 258 |
| `removeTrayIcon` | Removes tray icon from managed state; the implementation checks a guard condition before continuing. | 277 |
| `openNotification` | Performs the open notification operation for shell; the implementation prepares `panel` before completing the operation. | 289 |
| `notificationAcknowledged` | Performs the notification acknowledged operation for shell; the implementation checks a guard condition before continuing. | 313 |
| `buildTopBar` | Builds top bar; the implementation prepares `bar` before completing the operation. | 323 |
| `buildSidebar` | Builds sidebar; the implementation prepares `list` before completing the operation. | 379 |
| `show` | Displays ; the implementation stores the updated value in `activeModule`. | 404 |
| `refreshCurrentPage` | Refreshes current page; the implementation prepares `panel` before completing the operation. | 417 |
| `create` | Returns the result for create; the implementation selects and returns a value based on the current state. | 456 |

**record `AlertSnapshot`** (line 321) — Immutable data/value type for alert snapshot information. 0 explicitly declared constructor/method member(s).

**class `MenuIcon`** (line 424) — Named class supporting menu icon behavior in its containing package. 4 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the menu icon type; the implementation stores the updated value in `this.size`. | 427 |
| `paintIcon` | Performs the paint icon operation for menu icon; the implementation prepares `g2` before completing the operation. | 431 |
| `getIconWidth` | Returns the result for get icon width; the implementation returns the `size` field/value. | 445 |
| `getIconHeight` | Returns the result for get icon height; the implementation returns the `size` field/value. | 450 |


#### Folder `src/main/java/com/gudo/germs/ui/admin/`

##### `src/main/java/com/gudo/germs/ui/admin/AuditLogPanel.java`

**class `AuditLogPanel`** (line 31) — Swing panel responsible for the audit log screen/workflow. 5 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the audit log panel type; the implementation begins by calling super. | 46 |
| `buildHeader` | Builds header; the implementation prepares `header` before completing the operation. | 62 |
| `loadLogs` | Loads logs; the implementation begins by calling set text. | 108 |
| `inspectSelected` | Shows the details of the selected audit entry; the implementation prepares `r` before completing the operation. | 150 |
| `refreshData` | Refreshes data; the implementation begins by calling load logs. | 168 |

**class `<anonymous@36>`** (line 36) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `isCellEditable` | Reports whether cell editable; the implementation computes and returns the result of its checks or mapping. | 37 |

##### `src/main/java/com/gudo/germs/ui/admin/DashboardPanel.java`

**class `DashboardPanel`** (line 52) — Swing panel responsible for the dashboard screen/workflow. 13 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the dashboard panel type; the implementation begins by calling super. | 107 |
| `buildHeader` | Builds header; the implementation prepares `header` before completing the operation. | 123 |
| `buildClassificationControls` | Builds classification controls; the implementation prepares `controls` before completing the operation. | 158 |
| `loadResponderClassification` | Loads responder classification; the implementation stores the updated value in `classificationLoaded`. | 180 |
| `classificationFor` | Performs the classification for operation for dashboard panel; the implementation prepares `normalized` before completing the operation. | 213 |
| `updateClassificationSaveState` | Updates classification save state; the implementation prepares `selected` before completing the operation. | 223 |
| `saveResponderClassification` | Performs the save responder classification operation for dashboard panel; the implementation prepares `selected` before completing the operation. | 228 |
| `buildCenterContent` | Builds center content; the implementation prepares `center` before completing the operation. | 263 |
| `buildFooter` | Builds footer; the implementation prepares `footer` before completing the operation. | 393 |
| `addNotify` | Adds notify to managed state; the implementation begins by calling add notify. | 401 |
| `removeNotify` | Removes notify from managed state; the implementation checks a guard condition before continuing. | 409 |
| `refreshAllData` | Refreshes all data; the implementation begins by calling set text. | 418 |
| `refreshData` | Refreshes data; the implementation begins by calling refresh all data. | 530 |

**class `<anonymous@86>`** (line 86) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `isCellEditable` | Reports whether cell editable; the implementation computes and returns the result of its checks or mapping. | 87 |

**class `<anonymous@96>`** (line 96) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `isCellEditable` | Reports whether cell editable; the implementation computes and returns the result of its checks or mapping. | 97 |

**record `ClassificationOption`** (line 256) — Immutable data/value type for classification option information. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `toString` | Formats a display string for classification option; the implementation returns the `label` field/value. | 257 |

**class `<anonymous@314>`** (line 314) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `getTableCellRendererComponent` | Returns the table cell renderer component value; the implementation begins by calling get table cell renderer component. | 315 |

**class `<anonymous@326>`** (line 326) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `getTableCellRendererComponent` | Returns the table cell renderer component value; the implementation begins by calling get table cell renderer component. | 327 |

##### `src/main/java/com/gudo/germs/ui/admin/DispatchPanel.java`

**class `DispatchPanel`** (line 24) — Swing panel responsible for the dispatch screen/workflow. 17 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the dispatch panel type; the implementation begins by calling super. | 52 |
| `buildHeader` | Builds header; the implementation prepares `header` before completing the operation. | 65 |
| `buildSplit` | Builds split; the implementation begins by calling set auto create row sorter. | 91 |
| `buildFooter` | Builds footer; the implementation prepares `f` before completing the operation. | 144 |
| `addNotify` | Adds notify to managed state; the implementation begins by calling add notify. | 174 |
| `removeNotify` | Removes notify from managed state; the implementation begins by calling remove notify. | 179 |
| `refreshData` | Refreshes data; the implementation begins by calling load incidents. | 183 |
| `loadIncidents` | Loads incidents; the implementation begins by calling run. | 188 |
| `selectedIncident` | Returns the result for selected incident; the implementation computes and returns the result of its checks or mapping. | 205 |
| `refreshResponses` | Refreshes responses; the implementation prepares `incident` before completing the operation. | 209 |
| `renderResponses` | Renders responses; the implementation begins by calling set row count. | 229 |
| `confirmAssignment` | Performs the confirm assignment operation for dispatch panel; the implementation prepares `incident` before completing the operation. | 272 |
| `hasAcceptedSelection` | Reports whether accepted selection; the implementation computes and returns the result of its checks or mapping. | 309 |
| `cancelSelectedAssignment` | Performs the cancel selected assignment operation for dispatch panel; the implementation prepares `r` before completing the operation. | 315 |
| `showRequestPersonnelDialog` | Displays request personnel dialog; the implementation prepares `inc` before completing the operation. | 340 |
| `resolveSelectedIncident` | Performs the resolve selected incident operation for dispatch panel; the implementation prepares `inc` before completing the operation. | 367 |
| `showError` | Displays error; the implementation begins by calling set foreground. | 384 |

**class `<anonymous@30>`** (line 30) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `isCellEditable` | Reports whether cell editable; the implementation computes and returns the result of its checks or mapping. | 31 |

**class `<anonymous@36>`** (line 36) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `isCellEditable` | Reports whether cell editable; the implementation computes and returns the result of its checks or mapping. | 37 |

##### `src/main/java/com/gudo/germs/ui/admin/EvacuationCenterPanel.java`

**class `EvacuationCenterPanel`** (line 39) — Swing panel responsible for the evacuation center screen/workflow. 8 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the evacuation center panel type; the implementation begins by calling super. | 56 |
| `buildHeader` | Builds header; the implementation prepares `header` before completing the operation. | 73 |
| `loadCenters` | Loads centers; the implementation begins by calling set text. | 126 |
| `getSelectedCenterId` | Returns the selected center id value; the implementation prepares `r` before completing the operation. | 169 |
| `showAddCenterDialog` | Displays add center dialog; the implementation prepares `name` before completing the operation. | 179 |
| `showUpdateOccupancyDialog` | Displays update occupancy dialog; the implementation prepares `id` before completing the operation. | 251 |
| `showChangeStatusDialog` | Displays change status dialog; the implementation prepares `id` before completing the operation. | 288 |
| `refreshData` | Refreshes data; the implementation begins by calling load centers. | 304 |

**class `<anonymous@47>`** (line 47) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `isCellEditable` | Reports whether cell editable; the implementation computes and returns the result of its checks or mapping. | 48 |

##### `src/main/java/com/gudo/germs/ui/admin/LiveOperationsMapPanel.java`

**class `LiveOperationsMapPanel`** (line 41) — Swing panel responsible for the live operations map screen/workflow. 8 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the live operations map panel type; the implementation begins by calling super. | 66 |
| `buildHeader` | Builds header; the implementation prepares `p` before completing the operation. | 79 |
| `buildBottomBar` | Builds bottom bar; the implementation prepares `bottom` before completing the operation. | 115 |
| `controlGroup` | Performs the control group operation for live operations map panel; the implementation prepares `g` before completing the operation. | 152 |
| `buildMainSplit` | Builds main split; the implementation begins by calling set row height. | 163 |
| `loadData` | Loads data; the implementation begins by calling set text. | 190 |
| `applyFilters` | Applies filters; the implementation prepares `inc` before completing the operation. | 279 |
| `refreshData` | Refreshes data; the implementation begins by calling load data. | 313 |

**class `<anonymous@55>`** (line 55) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `isCellEditable` | Reports whether cell editable; the implementation computes and returns the result of its checks or mapping. | 56 |

##### `src/main/java/com/gudo/germs/ui/admin/ReportsAnalyticsPanel.java`

**class `ReportsAnalyticsPanel`** (line 26) — Swing panel responsible for the reports analytics screen/workflow. 7 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the reports analytics panel type; the implementation begins by calling super. | 47 |
| `buildHeader` | Builds header; the implementation prepares `header` before completing the operation. | 60 |
| `buildCenterArea` | Builds center area; the implementation prepares `center` before completing the operation. | 99 |
| `loadAnalytics` | Loads analytics; the implementation begins by calling set text. | 121 |
| `filterData` | Filters data; the implementation checks a guard condition before continuing. | 164 |
| `exportCsv` | Exports csv; the implementation prepares `chooser` before completing the operation. | 194 |
| `refreshData` | Refreshes data; the implementation begins by calling load analytics. | 218 |

**class `<anonymous@36>`** (line 36) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `isCellEditable` | Reports whether cell editable; the implementation computes and returns the result of its checks or mapping. | 37 |

##### `src/main/java/com/gudo/germs/ui/admin/ResourceManagementPanel.java`

**class `ResourceManagementPanel`** (line 40) — Swing panel responsible for the resource management screen/workflow. 10 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the resource management panel type; the implementation begins by calling super. | 65 |
| `buildHeader` | Builds header; the implementation prepares `header` before completing the operation. | 78 |
| `buildSplit` | Builds split; the implementation begins by calling set row height. | 127 |
| `loadResources` | Loads resources; the implementation begins by calling set text. | 159 |
| `getSelectedResourceId` | Returns the selected resource id value; the implementation prepares `r` before completing the operation. | 189 |
| `loadAllocationsForSelected` | Loads allocations for selected; the implementation prepares `resId` before completing the operation. | 196 |
| `showAddResourceDialog` | Displays add resource dialog; the implementation prepares `name` before completing the operation. | 231 |
| `allocateSelectedResource` | Performs the allocate selected resource operation for resource management panel; the implementation prepares `resId` before completing the operation. | 284 |
| `releaseSelectedAllocation` | Performs the release selected allocation operation for resource management panel; the implementation prepares `r` before completing the operation. | 349 |
| `refreshData` | Refreshes data; the implementation begins by calling load resources. | 393 |

**class `<anonymous@46>`** (line 46) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `isCellEditable` | Reports whether cell editable; the implementation computes and returns the result of its checks or mapping. | 47 |

**class `<anonymous@55>`** (line 55) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `isCellEditable` | Reports whether cell editable; the implementation computes and returns the result of its checks or mapping. | 56 |

##### `src/main/java/com/gudo/germs/ui/admin/TeamManagementPanel.java`

**class `TeamManagementPanel`** (line 40) — Swing panel responsible for the team management screen/workflow. 11 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the team management panel type; the implementation begins by calling super. | 65 |
| `buildHeader` | Builds header; the implementation prepares `header` before completing the operation. | 78 |
| `buildBodySplit` | Builds body split; the implementation begins by calling set row height. | 130 |
| `loadTeams` | Loads teams; the implementation begins by calling set text. | 160 |
| `getSelectedTeamId` | Returns the selected team id value; the implementation prepares `r` before completing the operation. | 198 |
| `loadMembersForSelectedTeam` | Loads members for selected team; the implementation prepares `teamId` before completing the operation. | 205 |
| `showCreateTeamDialog` | Displays create team dialog; the implementation prepares `nameField` before completing the operation. | 238 |
| `showAddMemberDialog` | Displays add member dialog; the implementation prepares `teamId` before completing the operation. | 280 |
| `removeSelectedMember` | Removes selected member from managed state; the implementation prepares `r` before completing the operation. | 341 |
| `dispatchSelectedTeam` | Performs the dispatch selected team operation for team management panel; the implementation prepares `teamId` before completing the operation. | 367 |
| `refreshData` | Refreshes data; the implementation begins by calling load teams. | 413 |

**class `<anonymous@45>`** (line 45) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `isCellEditable` | Reports whether cell editable; the implementation computes and returns the result of its checks or mapping. | 46 |

**class `<anonymous@54>`** (line 54) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `isCellEditable` | Reports whether cell editable; the implementation computes and returns the result of its checks or mapping. | 55 |

**record `ProfileItem`** (line 406) — Immutable data/value type for profile item information. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `toString` | Formats a display string for profile item; the implementation returns the `display` field/value. | 407 |

##### `src/main/java/com/gudo/germs/ui/admin/UserManagementPanel.java`

**class `UserManagementPanel`** (line 23) — Swing panel responsible for the user management screen/workflow. 11 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the user management panel type; the implementation begins by calling super. | 39 |
| `load` | Loads ; the implementation prepares `sel` before completing the operation. | 109 |
| `refreshData` | Refreshes data; the implementation begins by calling load. | 127 |
| `render` | Renders ; the implementation begins by calling set row count. | 132 |
| `selectedId` | Selects ed id; the implementation prepares `row` before completing the operation. | 180 |
| `promoteSelected` | Performs the promote selected operation for user management panel; the implementation prepares `id` before completing the operation. | 190 |
| `addAccount` | Adds account to managed state; the implementation prepares `name` before completing the operation. | 252 |
| `editSelected` | Performs the edit selected operation for user management panel; the implementation prepares `id` before completing the operation. | 302 |
| `setAvailability` | Sets availability; the implementation prepares `id` before completing the operation. | 380 |
| `requestDeployment` | Performs the request deployment operation for user management panel; the implementation prepares `id` before completing the operation. | 403 |
| `deactivateSelected` | Performs the deactivate selected operation for user management panel; the implementation prepares `id` before completing the operation. | 447 |

**class `<anonymous@28>`** (line 28) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `isCellEditable` | Reports whether cell editable; the implementation computes and returns the result of its checks or mapping. | 29 |

**record `ClassificationOption`** (line 373) — Immutable data/value type for classification option information. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `toString` | Formats a display string for classification option; the implementation returns the `label` field/value. | 374 |


#### Folder `src/main/java/com/gudo/germs/ui/auth/`

##### `src/main/java/com/gudo/germs/ui/auth/Background3D.java`

**class `Background3D`** (line 29) — Named class supporting background3 d behavior in its containing package. 12 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the background3 d type; the implementation stores the updated value in `this.isDarkTheme`. | 56 |
| `setPaused` | Sets paused; the implementation stores the updated value in `this.isPaused`. | 116 |
| `setAccentColor` | Sets accent color; the implementation stores the updated value in `this.accentColor`. | 125 |
| `updatePhysics` | Updates physics; the implementation begins with the implementation shown at this source location. | 129 |
| `initGeometries` | Performs the init geometries operation for background3 d; the implementation begins by calling add. | 150 |
| `paintComponent` | Performs the paint component operation for background3 d; the implementation begins by calling paint component. | 156 |
| `paintAtmosphericGradient` | Performs the paint atmospheric gradient operation for background3 d; the implementation prepares `p1` before completing the operation. | 174 |
| `renderScene3D` | Renders scene3 d; the implementation prepares `fov` before completing the operation. | 207 |
| `paintRipples` | Performs the paint ripples operation for background3 d; the implementation begins with the implementation shown at this source location. | 286 |
| `createIcosahedron` | Creates icosahedron; the implementation prepares `m` before completing the operation. | 303 |
| `createFacetedRing` | Creates faceted ring; the implementation prepares `m` before completing the operation. | 324 |
| `updateTheme` | Updates theme; the implementation stores the updated value in `this.isDarkTheme`. | 341 |

**class `Ripple`** (line 45) — Named class supporting ripple behavior in its containing package. 0 explicitly declared constructor/method member(s).

**class `<anonymous@64>`** (line 64) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 4 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `mousePressed` | Performs the mouse pressed operation for <anonymous@64>; the implementation stores the updated value in `lastMousePos`. | 65 |
| `mouseDragged` | Performs the mouse dragged operation for <anonymous@64>; the implementation checks a guard condition before continuing. | 74 |
| `mouseMoved` | Performs the mouse moved operation for <anonymous@64>; the implementation prepares `cx` before completing the operation. | 87 |
| `mouseReleased` | Performs the mouse released operation for <anonymous@64>; the implementation stores the updated value in `lastMousePos`. | 97 |

**class `Point3D`** (line 298) — Named class supporting point3 d behavior in its containing package. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the point3 d type; the implementation stores the updated value in `this.x`. | 298 |

**class `Face3D`** (line 299) — Named class supporting face3 d behavior in its containing package. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the face3 d type; the implementation stores the updated value in `this.indices`. | 299 |

**class `Mesh3D`** (line 300) — Named class supporting mesh3 d behavior in its containing package. 0 explicitly declared constructor/method member(s).

**class `FacePolygon`** (line 301) — Named class supporting face polygon behavior in its containing package. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the face polygon type; the implementation stores the updated value in `path`. | 301 |

##### `src/main/java/com/gudo/germs/ui/auth/LoginPanel.java`

**class `LoginPanel`** (line 58) — Swing panel responsible for the login screen/workflow. 17 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the login panel type; the implementation stores the updated value in `this.ctx`. | 97 |
| `buildSplitGlassCard` | Builds split glass card; the implementation prepares `card` before completing the operation. | 126 |
| `buildLeftBrandPanel` | Builds left brand panel; the implementation prepares `left` before completing the operation. | 141 |
| `createLogoComponent` | Creates logo component; the implementation prepares `logoUrl` before completing the operation. | 190 |
| `createBulletBadge` | Creates bullet badge; the implementation prepares `badge` before completing the operation. | 206 |
| `buildRightFormPanel` | Builds right form panel; the implementation prepares `right` before completing the operation. | 220 |
| `createAlignedLabelRow` | Creates aligned label row; the implementation prepares `row` before completing the operation. | 323 |
| `applyTheme` | Applies theme; the implementation prepares `textHeading` before completing the operation. | 336 |
| `removeNotify` | Removes notify from managed state; the implementation begins by calling remove theme listener. | 364 |
| `shakeComponent` | Performs the shake component operation for login panel; the implementation prepares `origin` before completing the operation. | 373 |
| `attemptLogin` | Performs the attempt login operation for login panel; the implementation prepares `email` before completing the operation. | 394 |
| `showRegisterDialog` | Displays register dialog; the implementation prepares `parentWindow` before completing the operation. | 424 |
| `setBusy` | Sets busy; the implementation begins by calling set enabled. | 440 |
| `showError` | Displays error; the implementation begins by calling set text. | 449 |
| `clearError` | Performs the clear error operation for login panel; the implementation begins by calling set text. | 454 |
| `friendly` | Performs the friendly operation for login panel; the implementation prepares `msg` before completing the operation. | 458 |
| `createLabel` | Creates label; the implementation prepares `l` before completing the operation. | 466 |

**class `<anonymous@142>`** (line 142) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `paintComponent` | Performs the paint component operation for <anonymous@142>; the implementation prepares `g2` before completing the operation. | 143 |

**class `<anonymous@377>`** (line 377) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `actionPerformed` | Performs the action performed operation for <anonymous@377>; the implementation checks a guard condition before continuing. | 379 |

**interface `RegistrationHandler`** (line 477) — Contract for registration handler behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `handle` | Defines the handle contract for this type (abstract/interface declaration). | 479 |

**class `GlassRegistrationDialog`** (line 482) — Named class supporting glass registration dialog behavior in its containing package. 6 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the glass registration dialog type; the implementation begins by calling super. | 499 |
| `startFadeIn` | Performs the start fade in operation for glass registration dialog; the implementation stores the updated value in `animProgress`. | 644 |
| `fadeOutAndDispose` | Performs the fade out and dispose operation for glass registration dialog; the implementation checks a guard condition before continuing. | 673 |
| `trySetWindowOpacity` | Performs the try set window opacity operation for glass registration dialog; the implementation prepares `clamped` before completing the operation. | 701 |
| `createModalRow` | Creates modal row; the implementation prepares `p` before completing the operation. | 712 |
| `setDialogError` | Sets dialog error; the implementation begins by calling set text. | 727 |

**class `<anonymous@505>`** (line 505) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `paintComponent` | Performs the paint component operation for <anonymous@505>; the implementation prepares `g2` before completing the operation. | 506 |

**class `<anonymous@636>`** (line 636) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `windowOpened` | Performs the window opened operation for <anonymous@636>; the implementation begins by calling start fade in. | 637 |

**class `ThemeToggleBar`** (line 736) — Named class supporting theme toggle bar behavior in its containing package. 4 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the theme toggle bar type; the implementation begins by calling set layout. | 740 |
| `configureBtn` | Performs the configure btn operation for theme toggle bar; the implementation begins by calling set font. | 753 |
| `refreshThemeState` | Refreshes theme state; the implementation checks a guard condition before continuing. | 766 |
| `paintComponent` | Performs the paint component operation for theme toggle bar; the implementation prepares `g2` before completing the operation. | 779 |

**class `RoundedTextField`** (line 812) — Named class supporting rounded text field behavior in its containing package. 5 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the rounded text field type; the implementation begins by calling set layout. | 816 |
| `getText` | Returns the result for get text; the implementation computes and returns the result of its checks or mapping. | 829 |
| `getField` | Returns the result for get field; the implementation returns the `field` field/value. | 830 |
| `setTheme` | Sets theme; the implementation stores the updated value in `this.isDark`. | 832 |
| `paintComponent` | Performs the paint component operation for rounded text field; the implementation prepares `g2` before completing the operation. | 851 |

**class `RoundedPasswordField`** (line 873) — Named class supporting rounded password field behavior in its containing package. 5 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the rounded password field type; the implementation begins by calling set layout. | 879 |
| `getPassword` | Returns the result for get password; the implementation computes and returns the result of its checks or mapping. | 910 |
| `getField` | Returns the result for get field; the implementation returns the `field` field/value. | 911 |
| `setTheme` | Sets theme; the implementation stores the updated value in `this.isDark`. | 913 |
| `paintComponent` | Performs the paint component operation for rounded password field; the implementation prepares `g2` before completing the operation. | 934 |

**class `GlassCard`** (line 958) — Named class supporting glass card behavior in its containing package. 3 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the glass card type; the implementation stores the updated value in `this.radius`. | 962 |
| `setTheme` | Sets theme; the implementation stores the updated value in `this.isDark`. | 967 |
| `paintComponent` | Performs the paint component operation for glass card; the implementation prepares `g2` before completing the operation. | 972 |

**class `MacPillButton`** (line 1006) — Named class supporting mac pill button behavior in its containing package. 3 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the mac pill button type; the implementation begins by calling super. | 1010 |
| `setTheme` | Sets theme; the implementation stores the updated value in `this.isDark`. | 1020 |
| `paintComponent` | Performs the paint component operation for mac pill button; the implementation prepares `g2` before completing the operation. | 1025 |

**class `LoadingSpinner`** (line 1059) — Named class supporting loading spinner behavior in its containing package. 3 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the loading spinner type; the implementation begins by calling set preferred size. | 1064 |
| `setActive` | Sets active; the implementation stores the updated value in `this.active`. | 1072 |
| `paintComponent` | Performs the paint component operation for loading spinner; the implementation checks a guard condition before continuing. | 1079 |


#### Folder `src/main/java/com/gudo/germs/ui/components/`

##### `src/main/java/com/gudo/germs/ui/components/EmergencyContactDirectoryPanel.java`

**class `EmergencyContactDirectoryPanel`** (line 35) — Swing panel responsible for the emergency contact directory screen/workflow. 5 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the emergency contact directory panel type; the implementation begins by calling super. | 53 |
| `buildHeader` | Builds header; the implementation prepares `header` before completing the operation. | 71 |
| `loadContacts` | Loads contacts; the implementation begins by calling set text. | 127 |
| `showAddContactDialog` | Displays add contact dialog; the implementation prepares `name` before completing the operation. | 160 |
| `refreshData` | Refreshes data; the implementation begins by calling load contacts. | 215 |

**class `<anonymous@43>`** (line 43) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `isCellEditable` | Reports whether cell editable; the implementation computes and returns the result of its checks or mapping. | 44 |

##### `src/main/java/com/gudo/germs/ui/components/LocationPickerDialog.java`

**class `LocationPickerDialog`** (line 18) — Named class supporting location picker dialog behavior in its containing package. 2 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Provides an intentionally empty <init> implementation. | 22 |
| `showDialog` | Returns the result for show dialog; the implementation computes and returns the result of its checks or mapping. | 24 |

**record `Selection`** (line 19) — Immutable data/value type for selection information. 0 explicitly declared constructor/method member(s).

**class `Picker`** (line 28) — Named class supporting picker behavior in its containing package. 7 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the picker type; the implementation stores the updated value in `dialog`. | 39 |
| `show` | Displays ; the implementation prepares `controls` before completing the operation. | 53 |
| `pin` | Performs the pin operation for picker; the implementation stores the updated value in `selection`. | 99 |
| `searchPlaces` | Performs the search places operation for picker; the implementation prepares `query` before completing the operation. | 127 |
| `centerOnResult` | Performs the center on result operation for picker; the implementation prepares `index` before completing the operation. | 162 |
| `useApproximateLocation` | Performs the use approximate location operation for picker; the implementation prepares `confirm` before completing the operation. | 172 |
| `updateSelectionText` | Updates selection text; the implementation checks a guard condition before continuing. | 203 |

**class `<anonymous@89>`** (line 89) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `windowClosed` | Performs the window closed operation for <anonymous@89>; the implementation begins by calling set pin drop enabled. | 90 |

**class `<anonymous@102>`** (line 102) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 2 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `doInBackground` | Performs the do in background operation for <anonymous@102>; the implementation runs its operation with guarded error handling. | 103 |
| `done` | Performs the done operation for <anonymous@102>; the implementation checks a guard condition before continuing. | 111 |

**class `<anonymous@134>`** (line 134) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 2 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `doInBackground` | Returns the result for do in background; the implementation computes and returns the result of its checks or mapping. | 135 |
| `done` | Performs the done operation for <anonymous@134>; the implementation runs its operation with guarded error handling. | 139 |

**class `<anonymous@181>`** (line 181) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 2 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `doInBackground` | Returns the result for do in background; the implementation computes and returns the result of its checks or mapping. | 182 |
| `done` | Performs the done operation for <anonymous@181>; the implementation runs its operation with guarded error handling. | 186 |

##### `src/main/java/com/gudo/germs/ui/components/ModulePlaceholderPanel.java`

**class `ModulePlaceholderPanel`** (line 13) — Swing panel responsible for the module placeholder screen/workflow. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the module placeholder panel type; the implementation begins by calling set layout. | 14 |

##### `src/main/java/com/gudo/germs/ui/components/OpenStreetMapPanel.java`

**class `OpenStreetMapPanel`** (line 32) — Swing panel responsible for the open street map screen/workflow. 24 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the open street map panel type; the implementation begins by calling this. | 75 |
| `constructor` | Initializes the open street map panel type; the implementation stores the updated value in `this.centerLat`. | 79 |
| `setPinDropEnabled` | Sets pin drop enabled; the implementation stores the updated value in `this.pinDropEnabled`. | 96 |
| `setCenter` | Sets center; the implementation stores the updated value in `this.centerLat`. | 102 |
| `addMarker` | Adds marker to managed state; the implementation begins by calling add. | 109 |
| `setMarkers` | Sets markers; the implementation begins by calling clear. | 114 |
| `clearMarkers` | Performs the clear markers operation for open street map panel; the implementation begins by calling clear. | 122 |
| `setSelectedCoordinate` | Sets selected coordinate; the implementation stores the updated value in `this.selectedCoord`. | 127 |
| `getSelectedCoordinate` | Returns the result for get selected coordinate; the implementation returns the `selectedCoord` field/value. | 132 |
| `shutdownTileLoader` | Performs the shutdown tile loader operation for open street map panel; the implementation begins by calling shutdown now. | 136 |
| `initMouseControls` | Performs the init mouse controls operation for open street map panel; the implementation begins by calling add mouse listener. | 140 |
| `zoomIn` | Performs the zoom in operation for open street map panel; the implementation checks a guard condition before continuing. | 230 |
| `zoomOut` | Performs the zoom out operation for open street map panel; the implementation checks a guard condition before continuing. | 237 |
| `resetView` | Performs the reset view operation for open street map panel; the implementation stores the updated value in `this.centerLat`. | 244 |
| `paintComponent` | Performs the paint component operation for open street map panel; the implementation begins by calling paint component. | 251 |
| `drawMarker` | Performs the draw marker operation for open street map panel; the implementation prepares `p` before completing the operation. | 309 |
| `drawCustomPin` | Performs the draw custom pin operation for open street map panel; the implementation prepares `p` before completing the operation. | 357 |
| `drawOverlay` | Performs the draw overlay operation for open street map panel; the implementation prepares `attr` before completing the operation. | 387 |
| `latLonToScreen` | Performs the lat lon to screen operation for open street map panel; the implementation prepares `p` before completing the operation. | 417 |
| `screenToLatLon` | Performs the screen to lat lon operation for open street map panel; the implementation prepares `centerPx` before completing the operation. | 425 |
| `latLonToPixel` | Performs the lat lon to pixel operation for open street map panel; the implementation prepares `x` before completing the operation. | 432 |
| `pixelToLatLon` | Performs the pixel to lat lon operation for open street map panel; the implementation prepares `mapSize` before completing the operation. | 440 |
| `getTile` | Returns the tile value; the implementation prepares `key` before completing the operation. | 448 |
| `fetchTile` | Performs the fetch tile operation for open street map panel; the implementation runs its operation with guarded error handling. | 469 |

**enum `MarkerType`** (line 33) — Finite set of marker type values used by the application. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the marker type type; the implementation stores the updated value in `this.color`. | 42 |

**record `MapMarker`** (line 48) — Immutable data/value type for map marker information. 0 explicitly declared constructor/method member(s).

**class `<anonymous@141>`** (line 141) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 3 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `mousePressed` | Performs the mouse pressed operation for <anonymous@141>; the implementation checks a guard condition before continuing. | 142 |
| `mouseReleased` | Performs the mouse released operation for <anonymous@141>; the implementation stores the updated value in `dragStart`. | 149 |
| `mouseClicked` | Performs the mouse clicked operation for <anonymous@141>; the implementation checks a guard condition before continuing. | 154 |

**class `<anonymous@174>`** (line 174) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 2 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `mouseDragged` | Performs the mouse dragged operation for <anonymous@174>; the implementation checks a guard condition before continuing. | 175 |
| `mouseMoved` | Performs the mouse moved operation for <anonymous@174>; the implementation prepares `found` before completing the operation. | 192 |

##### `src/main/java/com/gudo/germs/ui/components/OperationalTablePanel.java`

**class `OperationalTablePanel`** (line 26) — Swing panel responsible for the operational table screen/workflow. 5 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the operational table panel type; the implementation begins by calling super. | 36 |
| `addNotify` | Adds notify to managed state; the implementation begins by calling add notify. | 74 |
| `load` | Loads ; the implementation begins by calling set text. | 79 |
| `showRows` | Displays rows; the implementation begins by calling set row count. | 88 |
| `display` | Performs the display operation for operational table panel; the implementation prepares `value` before completing the operation. | 100 |

**record `Column`** (line 27) — Immutable data/value type for column information. 0 explicitly declared constructor/method member(s).

**class `<anonymous@61>`** (line 61) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `isCellEditable` | Reports whether cell editable; the implementation computes and returns the result of its checks or mapping. | 62 |

##### `src/main/java/com/gudo/germs/ui/components/PublicAnnouncementsPanel.java`

**class `PublicAnnouncementsPanel`** (line 23) — Swing panel responsible for the public announcements screen/workflow. 5 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the public announcements panel type; the implementation begins by calling super. | 38 |
| `buildHeader` | Builds header; the implementation prepares `header` before completing the operation. | 76 |
| `loadAnnouncements` | Loads announcements; the implementation begins by calling set text. | 110 |
| `showCreateAnnouncementDialog` | Displays create announcement dialog; the implementation prepares `titleField` before completing the operation. | 144 |
| `refreshData` | Refreshes data; the implementation begins by calling load announcements. | 181 |

**class `<anonymous@31>`** (line 31) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `isCellEditable` | Reports whether cell editable; the implementation computes and returns the result of its checks or mapping. | 32 |

##### `src/main/java/com/gudo/germs/ui/components/StatCard.java`

**class `StatCard`** (line 17) — Named class supporting stat card behavior in its containing package. 4 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the stat card type; the implementation stores the updated value in `this.accent`. | 22 |
| `setValue` | Sets value; the implementation begins by calling set text. | 36 |
| `updateUI` | Updates ui; the implementation begins by calling update ui. | 38 |
| `paintComponent` | Performs the paint component operation for stat card; the implementation prepares `g2` before completing the operation. | 44 |

##### `src/main/java/com/gudo/germs/ui/components/StatusBadge.java`

**class `StatusBadge`** (line 15) — Named class supporting status badge behavior in its containing package. 6 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the status badge type; the implementation begins by calling super. | 18 |
| `severity` | Returns the result for severity; the implementation computes and returns the result of its checks or mapping. | 26 |
| `incidentStatus` | Returns the result for incident status; the implementation computes and returns the result of its checks or mapping. | 30 |
| `label` | Performs the label operation for status badge; the implementation checks a guard condition before continuing. | 34 |
| `getPreferredSize` | Returns the preferred size value; the implementation prepares `d` before completing the operation. | 40 |
| `paintComponent` | Performs the paint component operation for status badge; the implementation prepares `g2` before completing the operation. | 45 |

##### `src/main/java/com/gudo/germs/ui/components/SurfacePanel.java`

**class `SurfacePanel`** (line 9) — Swing panel responsible for the surface screen/workflow. 2 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the surface panel type; the implementation begins by calling super. | 12 |
| `updateUI` | Updates ui; the implementation begins by calling update ui. | 18 |


#### Folder `src/main/java/com/gudo/germs/ui/guest/`

##### `src/main/java/com/gudo/germs/ui/guest/GuestReportPanel.java`

**class `GuestReportPanel`** (line 25) — Swing panel responsible for the guest report screen/workflow. 2 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the guest report panel type; the implementation stores the updated value in `this.service`. | 37 |
| `send` | Performs the send operation for guest report panel; the implementation begins by calling set enabled. | 91 |

##### `src/main/java/com/gudo/germs/ui/guest/MySubmittedReportsPanel.java`

**class `MySubmittedReportsPanel`** (line 18) — Swing panel responsible for the my submitted reports screen/workflow. 4 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the my submitted reports panel type; the implementation begins by calling super. | 29 |
| `buildHeader` | Builds header; the implementation prepares `header` before completing the operation. | 45 |
| `loadReports` | Loads reports; the implementation begins by calling set text. | 67 |
| `refreshData` | Refreshes data; the implementation begins by calling load reports. | 104 |

**class `<anonymous@23>`** (line 23) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `isCellEditable` | Reports whether cell editable; the implementation computes and returns the result of its checks or mapping. | 24 |

##### `src/main/java/com/gudo/germs/ui/guest/SettingsPanel.java`

**class `SettingsPanel`** (line 21) — Swing panel responsible for the settings screen/workflow. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the settings panel type; the implementation begins by calling set layout. | 22 |


#### Folder `src/main/java/com/gudo/germs/ui/incident/`

##### `src/main/java/com/gudo/germs/ui/incident/EmergencyCommunicationPanel.java`

**class `EmergencyCommunicationPanel`** (line 63) — Swing panel responsible for the emergency communication screen/workflow. 42 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the emergency communication panel type; the implementation begins by calling super. | 181 |
| `updateUI` | Updates ui; the implementation begins by calling update ui. | 229 |
| `applyTheme` | Applies theme; the implementation begins by calling set foreground. | 235 |
| `paintComponent` | Performs the paint component operation for emergency communication panel; the implementation prepares `g2` before completing the operation. | 261 |
| `glow` | Performs the glow operation for emergency communication panel; the implementation begins by calling set paint. | 272 |
| `alpha` | Returns the result for alpha; the implementation computes and returns the result of its checks or mapping. | 278 |
| `hex` | Returns the result for hex; the implementation computes and returns the result of its checks or mapping. | 282 |
| `glass` | Performs the glass operation for emergency communication panel; the implementation begins by calling set paint. | 286 |
| `smooth` | Performs the smooth operation for emergency communication panel; the implementation begins by calling set rendering hint. | 297 |
| `accentFor` | Returns the result for accent for; the implementation selects and returns a value based on the current state. | 303 |
| `buildChannelPopup` | Builds channel popup; the implementation begins by calling set cell renderer. | 313 |
| `buildHeader` | Builds header; the implementation prepares `bar` before completing the operation. | 349 |
| `buildChatColumn` | Builds chat column; the implementation prepares `col` before completing the operation. | 391 |
| `buildComposer` | Builds composer; the implementation begins by calling set border. | 432 |
| `buildOptionsSidebar` | Builds options sidebar; the implementation prepares `card` before completing the operation. | 485 |
| `addNotify` | Adds notify to managed state; the implementation begins by calling add notify. | 523 |
| `removeNotify` | Removes notify from managed state; the implementation begins by calling stop. | 530 |
| `loadIncidents` | Loads incidents; the implementation begins by calling run. | 539 |
| `loadMessages` | Loads messages; the implementation prepares `inc` before completing the operation. | 575 |
| `sendMessage` | Performs the send message operation for emergency communication panel; the implementation prepares `inc` before completing the operation. | 595 |
| `refreshNow` | Refreshes now; the implementation begins by calling set spinning. | 632 |
| `applySelection` | Applies selection; the implementation prepares `inc` before completing the operation. | 643 |
| `renderHeader` | Renders header; the implementation begins by calling remove all. | 660 |
| `setLive` | Sets live; the implementation stores the updated value in `liveOk`. | 680 |
| `refreshSendState` | Refreshes send state; the implementation prepares `hasText` before completing the operation. | 686 |
| `setMode` | Sets mode; the implementation stores the updated value in `mode`. | 691 |
| `setSidebarOpen` | Sets sidebar open; the implementation stores the updated value in `sidebarOpen`. | 707 |
| `stepSlide` | Performs the step slide operation for emergency communication panel; the implementation prepares `f` before completing the operation. | 722 |
| `onFeedResized` | Performs the on feed resized operation for emergency communication panel; the implementation prepares `w` before completing the operation. | 731 |
| `maxBubbleText` | Performs the max bubble text operation for emergency communication panel; the implementation prepares `vw` before completing the operation. | 739 |
| `rebuildFeed` | Performs the rebuild feed operation for emergency communication panel; the implementation stores the updated value in `lastRows`. | 745 |
| `showPlaceholder` | Displays placeholder; the implementation stores the updated value in `placeholderActive`. | 799 |
| `dayRow` | Performs the day row operation for emergency communication panel; the implementation prepares `l` before completing the operation. | 815 |
| `senderId` | Performs the sender id operation for emergency communication panel; the implementation iterates over the values it processes. | 833 |
| `fetchMessages` | Performs the fetch messages operation for emergency communication panel; the implementation prepares `rows` before completing the operation. | 841 |
| `roleLabel` | Performs the role label operation for emergency communication panel; the implementation checks a guard condition before continuing. | 875 |
| `parseTime` | Parses or maps parse time data; the implementation runs its operation with guarded error handling. | 884 |
| `isNearBottom` | Reports whether near bottom; the implementation prepares `bar` before completing the operation. | 892 |
| `scrollToBottom` | Performs the scroll to bottom operation for emergency communication panel; the implementation begins by calling invoke later. | 897 |
| `escape` | Returns the result for escape; the implementation computes and returns the result of its checks or mapping. | 904 |
| `elide` | Performs the elide operation for emergency communication panel; the implementation checks a guard condition before continuing. | 908 |
| `drawIcon` | Performs the draw icon operation for emergency communication panel; the implementation prepares `g` before completing the operation. | 917 |

**class `Pal`** (line 73) — Named class supporting pal behavior in its containing package. 19 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `dark` | Returns the result for dark; the implementation computes and returns the result of its checks or mapping. | 74 |
| `bgTop` | Returns the result for bg top; the implementation computes and returns the result of its checks or mapping. | 76 |
| `bgBottom` | Returns the result for bg bottom; the implementation computes and returns the result of its checks or mapping. | 77 |
| `popup` | Returns the result for popup; the implementation computes and returns the result of its checks or mapping. | 78 |
| `title` | Returns the result for title; the implementation computes and returns the result of its checks or mapping. | 79 |
| `text` | Returns the result for text; the implementation computes and returns the result of its checks or mapping. | 80 |
| `dim` | Returns the result for dim; the implementation computes and returns the result of its checks or mapping. | 81 |
| `blue` | Returns the result for blue; the implementation computes and returns the result of its checks or mapping. | 82 |
| `amber` | Returns the result for amber; the implementation computes and returns the result of its checks or mapping. | 83 |
| `red` | Returns the result for red; the implementation computes and returns the result of its checks or mapping. | 84 |
| `green` | Returns the result for green; the implementation computes and returns the result of its checks or mapping. | 85 |
| `line` | Returns the result for line; the implementation computes and returns the result of its checks or mapping. | 87 |
| `edge` | Returns the result for edge; the implementation computes and returns the result of its checks or mapping. | 88 |
| `glassTop` | Returns the result for glass top; the implementation computes and returns the result of its checks or mapping. | 89 |
| `glassBottom` | Returns the result for glass bottom; the implementation computes and returns the result of its checks or mapping. | 92 |
| `tint` | Returns the result for tint; the implementation computes and returns the result of its checks or mapping. | 96 |
| `wash` | Returns the result for wash; the implementation computes and returns the result of its checks or mapping. | 98 |
| `washEdge` | Returns the result for wash edge; the implementation computes and returns the result of its checks or mapping. | 99 |
| `shadow` | Returns the result for shadow; the implementation computes and returns the result of its checks or mapping. | 102 |

**enum `Kind`** (line 107) — Finite set of kind values used by the application. 0 explicitly declared constructor/method member(s).

**class `<anonymous@206>`** (line 206) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `componentResized` | Performs the component resized operation for <anonymous@206>; the implementation begins by calling restart. | 207 |

**class `<anonymous@209>`** (line 209) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `componentResized` | Performs the component resized operation for <anonymous@209>; the implementation prepares `narrow` before completing the operation. | 210 |

**class `<anonymous@320>`** (line 320) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `mouseReleased` | Performs the mouse released operation for <anonymous@320>; the implementation begins by calling set visible. | 321 |

**class `<anonymous@449>`** (line 449) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `actionPerformed` | Performs the action performed operation for <anonymous@449>; the implementation begins by calling send message. | 450 |

**class `<anonymous@452>`** (line 452) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 3 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `insertUpdate` | Performs the insert update operation for <anonymous@452>; the implementation begins by calling refresh send state. | 453 |
| `removeUpdate` | Removes update from managed state; the implementation begins by calling refresh send state. | 454 |
| `changedUpdate` | Performs the changed update operation for <anonymous@452>; the implementation begins by calling refresh send state. | 455 |

**class `<anonymous@457>`** (line 457) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 2 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `focusGained` | Performs the focus gained operation for <anonymous@457>; the implementation begins by calling set focused. | 458 |
| `focusLost` | Performs the focus lost operation for <anonymous@457>; the implementation begins by calling set focused. | 459 |

**class `<anonymous@822>`** (line 822) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `getMaximumSize` | Returns the result for get maximum size; the implementation computes and returns the result of its checks or mapping. | 823 |

**class `DotIcon`** (line 990) — Named class supporting dot icon behavior in its containing package. 4 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the dot icon type; the implementation stores the updated value in `this.color`. | 992 |
| `getIconWidth` | Returns the result for get icon width; the implementation computes and returns the result of its checks or mapping. | 993 |
| `getIconHeight` | Returns the result for get icon height; the implementation computes and returns the result of its checks or mapping. | 994 |
| `paintIcon` | Performs the paint icon operation for dot icon; the implementation prepares `g2` before completing the operation. | 995 |

**class `IconButton`** (line 1007) — Named class supporting icon button behavior in its containing package. 5 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the icon button type; the implementation stores the updated value in `this.kind`. | 1023 |
| `setAccent` | Sets accent; the implementation stores the updated value in `this.accent`. | 1040 |
| `setActive` | Sets active; the implementation stores the updated value in `this.active`. | 1041 |
| `setSpinning` | Sets spinning; the implementation checks a guard condition before continuing. | 1043 |
| `paintComponent` | Performs the paint component operation for icon button; the implementation prepares `g2` before completing the operation. | 1048 |

**class `GlassButton`** (line 1083) — Named class supporting glass button behavior in its containing package. 4 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the glass button type; the implementation begins by calling set content area filled. | 1086 |
| `setAccent` | Sets accent; the implementation stores the updated value in `this.accent`. | 1097 |
| `getMaximumSize` | Returns the result for get maximum size; the implementation computes and returns the result of its checks or mapping. | 1099 |
| `paintComponent` | Performs the paint component operation for glass button; the implementation prepares `g2` before completing the operation. | 1101 |

**class `GlassPanel`** (line 1118) — Swing panel responsible for the glass screen/workflow. 3 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the glass panel type; the implementation begins by calling super. | 1126 |
| `setFocused` | Sets focused; the implementation stores the updated value in `this.focused`. | 1135 |
| `paintComponent` | Performs the paint component operation for glass panel; the implementation prepares `g2` before completing the operation. | 1141 |

**class `Divider`** (line 1152) — Named class supporting divider behavior in its containing package. 2 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `getPreferredSize` | Returns the result for get preferred size; the implementation computes and returns the result of its checks or mapping. | 1153 |
| `paintComponent` | Performs the paint component operation for divider; the implementation begins by calling set color. | 1155 |

**class `PlaceholderArea`** (line 1162) — Named class supporting placeholder area behavior in its containing package. 3 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the placeholder area type; the implementation begins by calling super. | 1165 |
| `setPlaceholder` | Sets placeholder; the implementation stores the updated value in `this.placeholder`. | 1167 |
| `paintComponent` | Performs the paint component operation for placeholder area; the implementation begins by calling paint component. | 1169 |

**class `FeedPanel`** (line 1184) — Swing panel responsible for the feed screen/workflow. 6 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the feed panel type; the implementation begins by calling set opaque. | 1185 |
| `getPreferredScrollableViewportSize` | Returns the result for get preferred scrollable viewport size; the implementation computes and returns the result of its checks or mapping. | 1191 |
| `getScrollableUnitIncrement` | Returns the result for get scrollable unit increment; the implementation computes and returns the result of its checks or mapping. | 1192 |
| `getScrollableBlockIncrement` | Returns the result for get scrollable block increment; the implementation computes and returns the result of its checks or mapping. | 1193 |
| `getScrollableTracksViewportWidth` | Returns the result for get scrollable tracks viewport width; the implementation computes and returns the result of its checks or mapping. | 1194 |
| `getScrollableTracksViewportHeight` | Returns the result for get scrollable tracks viewport height; the implementation computes and returns the result of its checks or mapping. | 1195 |

**class `SlideContainer`** (line 1199) — Named class supporting slide container behavior in its containing package. 4 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the slide container type; the implementation begins by calling super. | 1203 |
| `progress` | Returns the result for progress; the implementation returns the `progress` field/value. | 1210 |
| `setProgress` | Sets progress; the implementation stores the updated value in `progress`. | 1212 |
| `getPreferredSize` | Returns the preferred size value; the implementation prepares `d` before completing the operation. | 1219 |

**class `MessageRow`** (line 1226) — Named class supporting message row behavior in its containing package. 2 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the message row type; the implementation begins by calling super. | 1227 |
| `getMaximumSize` | Returns the result for get maximum size; the implementation computes and returns the result of its checks or mapping. | 1234 |

**class `MessageBlock`** (line 1238) — Named class supporting message block behavior in its containing package. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the message block type; the implementation begins by calling set layout. | 1239 |

**class `MessageBubble`** (line 1280) — Named class supporting message bubble behavior in its containing package. 5 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the message bubble type; the implementation begins by calling super. | 1286 |
| `urgent` | Returns the result for urgent; the implementation computes and returns the result of its checks or mapping. | 1303 |
| `special` | Returns the result for special; the implementation computes and returns the result of its checks or mapping. | 1304 |
| `getMaximumSize` | Returns the result for get maximum size; the implementation computes and returns the result of its checks or mapping. | 1307 |
| `paintComponent` | Performs the paint component operation for message bubble; the implementation prepares `g2` before completing the operation. | 1309 |

**class `ModeOption`** (line 1333) — Named class supporting mode option behavior in its containing package. 2 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the mode option type; the implementation stores the updated value in `this.key`. | 1339 |
| `paintComponent` | Performs the paint component operation for mode option; the implementation prepares `g2` before completing the operation. | 1358 |

**class `IncidentCell`** (line 1394) — Named class supporting incident cell behavior in its containing package. 4 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the incident cell type; the implementation begins by calling super. | 1401 |
| `getListCellRendererComponent` | Returns the list cell renderer component value; the implementation stores the updated value in `stripe`. | 1415 |
| `getPreferredSize` | Returns the preferred size value; the implementation prepares `d` before completing the operation. | 1428 |
| `paintComponent` | Performs the paint component operation for incident cell; the implementation prepares `g2` before completing the operation. | 1433 |

##### `src/main/java/com/gudo/germs/ui/incident/FieldSituationReportPanel.java`

**class `FieldSituationReportPanel`** (line 51) — Swing panel responsible for the field situation report screen/workflow. 10 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the field situation report panel type; the implementation begins by calling super. | 78 |
| `addNotify` | Adds notify to managed state; the implementation begins by calling add notify. | 90 |
| `refreshData` | Refreshes data; the implementation begins by calling load reports. | 96 |
| `buildHeader` | Builds header; the implementation prepares `header` before completing the operation. | 101 |
| `buildMainSplit` | Builds main split; the implementation begins by calling set row height. | 151 |
| `titledBlock` | Performs the titled block operation for field situation report panel; the implementation prepares `p` before completing the operation. | 196 |
| `loadReports` | Loads reports; the implementation begins by calling set text. | 212 |
| `showDetail` | Displays detail; the implementation prepares `r` before completing the operation. | 271 |
| `showSubmitSitrepDialog` | Displays submit sitrep dialog; the implementation begins by calling run. | 306 |
| `showAddFeedbackDialog` | Displays add feedback dialog; the implementation prepares `r` before completing the operation. | 408 |

**class `<anonymous@62>`** (line 62) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `isCellEditable` | Reports whether cell editable; the implementation computes and returns the result of its checks or mapping. | 63 |

##### `src/main/java/com/gudo/germs/ui/incident/IncidentPanel.java`

**class `IncidentPanel`** (line 52) — Swing panel responsible for the incident screen/workflow. 16 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the incident panel type; the implementation begins by calling super. | 88 |
| `buildHeader` | Builds header; the implementation prepares `header` before completing the operation. | 99 |
| `buildSplit` | Builds split; the implementation begins by calling set row height. | 133 |
| `addNotify` | Adds notify to managed state; the implementation begins by calling add notify. | 285 |
| `removeNotify` | Removes notify from managed state; the implementation checks a guard condition before continuing. | 294 |
| `refresh` | Refreshes ; the implementation prepares `keep` before completing the operation. | 303 |
| `refreshData` | Refreshes data; the implementation begins by calling refresh. | 323 |
| `loadTeams` | Loads teams; the implementation begins by calling run. | 328 |
| `selected` | Selects ed; the implementation prepares `r` before completing the operation. | 337 |
| `updateDetail` | Updates detail; the implementation prepares `i` before completing the operation. | 342 |
| `doAdvance` | Performs the do advance operation for incident panel; the implementation prepares `i` before completing the operation. | 414 |
| `doUpdateSeverity` | Performs the do update severity operation for incident panel; the implementation prepares `incident` before completing the operation. | 435 |
| `doAssign` | Performs the do assign operation for incident panel; the implementation prepares `i` before completing the operation. | 459 |
| `doRespond` | Performs the do respond operation for incident panel; the implementation prepares `incident` before completing the operation. | 471 |
| `reportDialog` | Performs the report dialog operation for incident panel; the implementation prepares `title` before completing the operation. | 487 |
| `showMessage` | Displays message; the implementation begins by calling set text. | 550 |

**class `<anonymous@64>`** (line 64) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `isCellEditable` | Reports whether cell editable; the implementation computes and returns the result of its checks or mapping. | 65 |

**class `<anonymous@150>`** (line 150) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `getTableCellRendererComponent` | Returns the table cell renderer component value; the implementation begins by calling get table cell renderer component. | 151 |

**class `DetailPanel`** (line 249) — Swing panel responsible for the detail screen/workflow. 6 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the detail panel type; the implementation begins by calling super. | 250 |
| `getPreferredScrollableViewportSize` | Returns the result for get preferred scrollable viewport size; the implementation computes and returns the result of its checks or mapping. | 255 |
| `getScrollableUnitIncrement` | Returns the result for get scrollable unit increment; the implementation computes and returns the result of its checks or mapping. | 260 |
| `getScrollableBlockIncrement` | Returns the result for get scrollable block increment; the implementation computes and returns the result of its checks or mapping. | 265 |
| `getScrollableTracksViewportWidth` | Returns the result for get scrollable tracks viewport width; the implementation computes and returns the result of its checks or mapping. | 270 |
| `getScrollableTracksViewportHeight` | Returns the result for get scrollable tracks viewport height; the implementation computes and returns the result of its checks or mapping. | 275 |

##### `src/main/java/com/gudo/germs/ui/incident/MyDeploymentsPanel.java`

**class `MyDeploymentsPanel`** (line 45) — Swing panel responsible for the my deployments screen/workflow. 16 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the my deployments panel type; the implementation begins by calling super. | 86 |
| `buildHeader` | Builds header; the implementation prepares `header` before completing the operation. | 104 |
| `buildDeploymentsTab` | Builds deployments tab; the implementation prepares `panel` before completing the operation. | 153 |
| `buildNotificationsTab` | Builds notifications tab; the implementation prepares `panel` before completing the operation. | 227 |
| `row` | Performs the row operation for my deployments panel; the implementation prepares `p` before completing the operation. | 321 |
| `addNotify` | Adds notify to managed state; the implementation begins by calling add notify. | 327 |
| `removeNotify` | Removes notify from managed state; the implementation checks a guard condition before continuing. | 334 |
| `refreshData` | Refreshes data; the implementation begins by calling load deployments. | 344 |
| `loadDeployments` | Loads deployments; the implementation begins by calling set text. | 350 |
| `loadNotifications` | Loads notifications; the implementation begins by calling run. | 388 |
| `advanceSelectedAssignment` | Validates and advances advance selected assignment; the implementation prepares `r` before completing the operation. | 440 |
| `acknowledgeSelected` | Performs the acknowledge selected operation for my deployments panel; the implementation prepares `r` before completing the operation. | 488 |
| `respondToSelectedIncident` | Records or processes respond to selected incident; the implementation prepares `selected` before completing the operation. | 508 |
| `transmitSelectedLocation` | Performs the transmit selected location operation for my deployments panel; the implementation checks a guard condition before continuing. | 535 |
| `chooseLocation` | Performs the choose location operation for my deployments panel; the implementation prepares `picked` before completing the operation. | 564 |
| `disableLocationSharing` | Performs the disable location sharing operation for my deployments panel; the implementation begins by calling run. | 576 |

**class `<anonymous@56>`** (line 56) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `isCellEditable` | Reports whether cell editable; the implementation computes and returns the result of its checks or mapping. | 57 |

**class `<anonymous@75>`** (line 75) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `isCellEditable` | Reports whether cell editable; the implementation computes and returns the result of its checks or mapping. | 76 |

**class `<anonymous@162>`** (line 162) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `getTableCellRendererComponent` | Returns the table cell renderer component value; the implementation begins by calling get table cell renderer component. | 163 |

**class `<anonymous@238>`** (line 238) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `getTableCellRendererComponent` | Returns the table cell renderer component value; the implementation begins by calling get table cell renderer component. | 239 |

**class `WrapLayout`** (line 594) — Named class supporting wrap layout behavior in its containing package. 5 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the wrap layout type; the implementation begins by calling super. | 595 |
| `preferredLayoutSize` | Returns the result for preferred layout size; the implementation computes and returns the result of its checks or mapping. | 599 |
| `minimumLayoutSize` | Performs the minimum layout size operation for wrap layout; the implementation prepares `d` before completing the operation. | 604 |
| `layoutSize` | Performs the layout size operation for wrap layout; the implementation begins with the implementation shown at this source location. | 611 |
| `addRow` | Adds row to managed state; the implementation stores the updated value in `dim.width`. | 648 |

##### `src/main/java/com/gudo/germs/ui/incident/MyTeamPanel.java`

**class `MyTeamPanel`** (line 18) — Swing panel responsible for the my team screen/workflow. 6 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the my team panel type; the implementation begins by calling super. | 33 |
| `buildHeader` | Builds header; the implementation prepares `p` before completing the operation. | 54 |
| `styledBox` | Performs the styled box operation for my team panel; the implementation prepares `b` before completing the operation. | 71 |
| `loadTeam` | Loads team; the implementation begins by calling run. | 85 |
| `loadTeammates` | Loads teammates; the implementation begins by calling run. | 118 |
| `refreshData` | Refreshes data; the implementation begins by calling load team. | 137 |

**class `<anonymous@28>`** (line 28) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `isCellEditable` | Reports whether cell editable; the implementation computes and returns the result of its checks or mapping. | 29 |

##### `src/main/java/com/gudo/germs/ui/incident/NotificationPanel.java`

**class `NotificationPanel`** (line 23) — Swing panel responsible for the notification screen/workflow. 10 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Initializes the notification panel type; the implementation begins by calling super. | 43 |
| `buildHeader` | Builds header; the implementation prepares `header` before completing the operation. | 61 |
| `buildFooter` | Builds footer; the implementation prepares `f` before completing the operation. | 108 |
| `addNotify` | Adds notify to managed state; the implementation begins by calling add notify. | 136 |
| `removeNotify` | Removes notify from managed state; the implementation begins by calling remove notify. | 141 |
| `refreshData` | Refreshes data; the implementation begins by calling load notifications. | 145 |
| `openNotification` | Performs the open notification operation for notification panel; the implementation stores the updated value in `pendingNotificationId`. | 150 |
| `loadNotifications` | Loads notifications; the implementation begins by calling run. | 155 |
| `receiptAcknowledged` | Performs the receipt acknowledged operation for notification panel; the implementation checks a guard condition before continuing. | 249 |
| `acknowledgeSelected` | Performs the acknowledge selected operation for notification panel; the implementation prepares `r` before completing the operation. | 254 |

**class `<anonymous@32>`** (line 32) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 1 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `isCellEditable` | Reports whether cell editable; the implementation computes and returns the result of its checks or mapping. | 33 |


#### Folder `src/main/java/com/gudo/germs/ui/theme/`

##### `src/main/java/com/gudo/germs/ui/theme/GermsColors.java`

**class `GermsColors`** (line 6) — Named class supporting germs colors behavior in its containing package. 7 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Provides an intentionally empty <init> implementation. | 7 |
| `background` | Returns the result for background; the implementation computes and returns the result of its checks or mapping. | 25 |
| `surface` | Returns the result for surface; the implementation computes and returns the result of its checks or mapping. | 26 |
| `text` | Returns the result for text; the implementation computes and returns the result of its checks or mapping. | 27 |
| `border` | Returns the result for border; the implementation computes and returns the result of its checks or mapping. | 28 |
| `forSeverity` | Returns the result for for severity; the implementation selects and returns a value based on the current state. | 30 |
| `forIncidentStatus` | Returns the result for for incident status; the implementation selects and returns a value based on the current state. | 39 |

##### `src/main/java/com/gudo/germs/ui/theme/ThemeManager.java`

**class `ThemeManager`** (line 25) — Shared state/lifecycle manager for theme manager. 8 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Provides an intentionally empty <init> implementation. | 31 |
| `isDark` | Reports whether dark; the implementation returns the `dark` field/value. | 33 |
| `addThemeListener` | Adds theme listener to managed state; the implementation checks a guard condition before continuing. | 38 |
| `removeThemeListener` | Removes theme listener from managed state; the implementation begins by calling remove. | 44 |
| `installSaved` | Performs the install saved operation for theme manager; the implementation begins by calling apply. | 48 |
| `toggle` | Converts toggle to its target representation; the implementation begins by calling apply. | 52 |
| `apply` | Applies ; the implementation stores the updated value in `dark`. | 57 |
| `baseFont` | Performs the base font operation for theme manager; the implementation prepares `available` before completing the operation. | 94 |


#### Folder `src/main/java/com/gudo/germs/utils/`

##### `src/main/java/com/gudo/germs/utils/BackgroundTask.java`

**class `BackgroundTask`** (line 9) — Named class supporting background task behavior in its containing package. 2 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Provides an intentionally empty <init> implementation. | 10 |
| `run` | Performs the run operation for background task; the implementation begins by calling execute. | 12 |

**class `<anonymous@13>`** (line 13) — Anonymous class implementing a local callback, renderer, worker, or adapter at this point; its methods below describe the concrete behavior. 2 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `doInBackground` | Returns the result for do in background; the implementation computes and returns the result of its checks or mapping. | 14 |
| `done` | Performs the done operation for <anonymous@13>; the implementation runs its operation with guarded error handling. | 16 |

##### `src/main/java/com/gudo/germs/utils/ResponderClassificationCodes.java`

**class `ResponderClassificationCodes`** (line 5) — Named class supporting responder classification codes behavior in its containing package. 3 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `constructor` | Provides an intentionally empty <init> implementation. | 6 |
| `canonicalCode` | Normalizes canonical code; the implementation checks a guard condition before continuing. | 8 |
| `initials` | Returns the result for initials; the implementation computes and returns the result of its checks or mapping. | 20 |


#### Folder `src/test/java/com/gudo/germs/models/`

##### `src/test/java/com/gudo/germs/models/IncidentLifecycleTest.java`

**class `IncidentLifecycleTest`** (line 10) — Test type covering incident lifecycle test behavior. 4 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `incidentBuilderCreatesValidIncidentWithCoordinates` | Performs the incident builder creates valid incident with coordinates operation for incident lifecycle test; the implementation prepares `i` before completing the operation. | 12 |
| `incidentBuilderEnforcesMandatoryFields` | Performs the incident builder enforces mandatory fields operation for incident lifecycle test; the implementation begins by calling assert throws. | 37 |
| `incidentStatusTransitionsFollowLinearLifecycle` | Performs the incident status transitions follow linear lifecycle operation for incident lifecycle test; the implementation prepares `draft` before completing the operation. | 49 |
| `statusKeyParsingHandlesAliasesRobustly` | Performs the status key parsing handles aliases robustly operation for incident lifecycle test; the implementation begins by calling assert equals. | 80 |

##### `src/test/java/com/gudo/germs/models/OperationalModelTest.java`

**class `OperationalModelTest`** (line 11) — Test type covering operational model test behavior. 4 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `roleParsingMapsGuestAndFallbacks` | Performs the role parsing maps guest and fallbacks operation for operational model test; the implementation begins by calling assert equals. | 13 |
| `profileParsingParsesValidJson` | Performs the profile parsing parses valid json operation for operational model test; the implementation prepares `mapper` before completing the operation. | 24 |
| `guestProfileFactoryCreatesValidDefaults` | Performs the guest profile factory creates valid defaults operation for operational model test; the implementation prepares `guest` before completing the operation. | 43 |
| `responseTeamModelSerializesProperly` | Performs the response team model serializes properly operation for operational model test; the implementation prepares `tid` before completing the operation. | 52 |


#### Folder `src/test/java/com/gudo/germs/security/`

##### `src/test/java/com/gudo/germs/security/AccessPolicyTest.java`

**class `AccessPolicyTest`** (line 11) — Test type covering access policy test behavior. 4 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `adminSeesAllFifteenCoreModules` | Performs the admin sees all fifteen core modules operation for access policy test; the implementation prepares `count` before completing the operation. | 12 |
| `responderCannotSeeAdministrativeModules` | Records or processes responder cannot see administrative modules; the implementation prepares `modules` before completing the operation. | 17 |
| `guestSeesOnlyPublicModules` | Performs the guest sees only public modules operation for access policy test; the implementation prepares `modules` before completing the operation. | 28 |
| `privilegedKeysAreRejected` | Performs the privileged keys are rejected operation for access policy test; the implementation prepares `payload` before completing the operation. | 35 |


#### Folder `src/test/java/com/gudo/germs/services/`

##### `src/test/java/com/gudo/germs/services/AlarmServiceTest.java`

**class `AlarmServiceTest`** (line 7) — Test type covering alarm service test behavior. 3 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `alarmServiceSingletonInstanceIsActive` | Performs the alarm service singleton instance is active operation for alarm service test; the implementation prepares `service` before completing the operation. | 9 |
| `muteToggleRespectsState` | Performs the mute toggle respects state operation for alarm service test; the implementation prepares `service` before completing the operation. | 16 |
| `triggerAlarmDeduplicatesAndRespectsMute` | Performs the trigger alarm deduplicates and respects mute operation for alarm service test; the implementation prepares `service` before completing the operation. | 26 |

##### `src/test/java/com/gudo/germs/services/GeoLocationServiceTest.java`

**class `GeoLocationServiceTest`** (line 7) — Test type covering geo location service test behavior. 2 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `acceptsValidLatitudeAndLongitudeBounds` | Performs the accepts valid latitude and longitude bounds operation for geo location service test; the implementation begins by calling assert true. | 8 |
| `rejectsOutOfRangeAndNonFiniteCoordinates` | Performs the rejects out of range and non finite coordinates operation for geo location service test; the implementation begins by calling assert false. | 15 |


#### Folder `src/test/java/com/gudo/germs/utils/`

##### `src/test/java/com/gudo/germs/utils/ResponderClassificationCodesTest.java`

**class `ResponderClassificationCodesTest`** (line 7) — Test type covering responder classification codes test behavior. 2 explicitly declared constructor/method member(s).

| Declared method | Purpose grounded in implementation | Source line |
|---|---|---:|
| `canonicalizesLegacyCodesToDisplayedInitials` | Normalizes canonicalizes legacy codes to displayed initials; the implementation begins by calling assert equals. | 8 |
| `keepsCurrentCodesAndNormalizesUnknownCodes` | Performs the keeps current codes and normalizes unknown codes operation for responder classification codes test; the implementation begins by calling assert equals. | 16 |

<!-- JAVA_INVENTORY_END -->
