# GERMS Architecture

GERMS is a Java 25 Swing desktop client built for emergency incident command and responder operations.
The Supabase project serves as the authoritative data and security layer: Supabase Auth manages user credentials, PostgreSQL enforces Row Level Security (RLS) and privileged `SECURITY DEFINER` RPCs, and PostgREST provides low-latency REST access with authenticated JWT propagation.

## System Layers

### 1. Presentation Layer (`com.gudo.germs.ui`)
- `MainWindow`: Top-level window swapping between login/registration card and authenticated `Shell`.
- `Shell`: Role-based navigation container displaying top status bar, active operational profile, theme toggle, and lazy card-layout module panels.
- `admin/`:
  - `DashboardPanel`: Operational command summary cards.
  - `DispatchPanel`: Live incident command, response monitoring, conflict-checked assignment confirmation, and operation resolution.
  - `LiveOperationsMapPanel`: Real-time OpenStreetMap GIS integrating incident markers, deployed responders, evacuation centers, and resources.
  - `UserManagementPanel`: Personnel directory, Guest-to-Responder role promotion, responder classifications, and availability controls.
  - `TeamManagementPanel`: Squad creation, team leader designation, member roster management, and team-based deployment.
  - `ResourceManagementPanel`: Emergency equipment inventory, condition tracking, and atomic incident allocation.
  - `EvacuationCenterPanel`: Evacuation shelter management (capacity, live occupancy, operating statuses).
  - `ReportsAnalyticsPanel`: Incident breakdown by severity/category, deployment completion rates, and CSV reporting.
  - `AuditLogPanel`: Append-only chronological audit log viewer.
- `incident/`:
  - `IncidentPanel`: Incident reporting, verification, lifecycle advancement, OpenStreetMap location pinning, and response decisions.
  - `FieldSituationReportPanel`: Deployed responder SITREP submission (casualties, field conditions, medical needs) and command feedback.
  - `EmergencyCommunicationPanel`: Incident-specific coordination channels, urgent broadcasts, and team threads.
  - `NotificationPanel`: Persistent notification inbox, alarm silencing, acknowledgement, and audio testing.
  - `MyDeploymentsPanel`: Responder assignment history, mission status advancement, and opt-in live GPS tracking.
  - `MyTeamPanel`: Responder assigned squad roster, team leader, and contact information.
- `guest/`:
  - `GuestReportPanel`: Public preliminary incident submission form.
  - `MySubmittedReportsPanel`: Citizen report verification tracking.
  - `SettingsPanel`: User profile information and visual theme customization.
- `components/`:
  - `OpenStreetMapPanel`: Pure Swing interactive Slippy Map tile renderer with memory/disk caching, pan, zoom, click-to-pin, and differentiated markers.
  - `PublicAnnouncementsPanel`: Emergency advisories, disaster warnings, and weather bulletins.
  - `EmergencyContactDirectoryPanel`: Directory of hotlines, hospitals, LGUs, and rescue organizations.
  - `StatCard`, `StatusBadge`, `SurfacePanel`: Standardized theme-aware UI components.

### 2. Services Layer (`com.gudo.germs.services`)
- `IncidentService`: Thread-safe application coordinator for incident reporting, guest submissions, team assignments, lifecycle advancement, and responses. Emits `IncidentEvent`s on EDT via `IncidentBus`.
- `AlarmService`: Emergency audio alarm generator synthesizing severity-based tones (Low, Moderate, High, Critical warble siren) using `javax.sound.sampled`, with repetition control, muting, and deduplication.
- `AuthService`: Authentication, session lifecycle, registration, and active profile verification.

### 3. Models Layer (`com.gudo.germs.models`)
- `Incident`: Immutable incident snapshot containing coordinates, description, timeline, status, and metadata.
- `IncidentBuilder`: Fluent builder with required field validation.
- `IncidentStatus`: State Pattern enum governing legal lifecycle forward transitions (`draft/submitted -> published -> acknowledged -> mobilizing -> active_response -> stabilized -> closed -> archived`).
- `Profile`: User record with `Role` (Admin, Responder, Guest) and activation flag.
- `ResponseTeam`: Response squad representation.
- `TimelineEntry`: Chronological incident event record.

### 4. Transport & Persistence Layer (`com.gudo.germs.supabase`, `com.gudo.germs.repositories`)
- `SupabaseHttp`: Central HTTP gateway using `java.net.http.HttpClient` configured with anon key and user JWT. Rejects privileged service-role credentials.
- `RestClient`: PostgREST query and RPC execution client.
- `SessionManager`: Memory-only JWT token manager with automatic 60-second expiration refresh.
- `SupabaseIncidentRepository`: PostgREST and RPC implementation of `IncidentRepository`.

### 5. Security & Access Control (`com.gudo.germs.security`, `database/migrations`)
- UI `AccessPolicy`: Presentation filter mapping roles to visible modules.
- Database Migrations 001–012: Authoritative security boundary enforcing Postgres Row Level Security (RLS) on all tables, and `SECURITY DEFINER` RPCs checking caller role (`is_admin()`, `is_responder()`). The auth signup trigger pins its search path and schema-qualifies the profile role type; migration 011 declares the audit actor-to-profile relationship for PostgREST; migration 012 restricts incident severity changes to administrators and actively assigned responders.

## GIS & OpenStreetMap Architecture
The interactive map uses standard OpenStreetMap Slippy Map tiles rendered in Java Swing:
- Coordinates are projected between latitude/longitude (WGS84) and tile pixel space using spherical Mercator projection.
- Tiles are fetched asynchronously via background worker threads with memory LRU and local disk caching in `.germs_osm_tiles`.
- Map markers render with distinct iconography and tooltips for Incidents (Red), Responders (Blue), Evacuation Shelters (Green), and Resources (Orange).
- Pin-drop mode enables administrators to click anywhere on the map to capture exact real coordinates (e.g. 7.0731, 125.6128).

## Emergency Audio Alarm Architecture
Emergency alarms do not rely on missing audio assets. The `AlarmService` synthesizes PCM audio waveforms mathematically:
- **Low**: 880 Hz sine wave chime with smooth exponential decay.
- **Moderate**: Ascending dual alert chime (659 Hz -> 880 Hz).
- **High**: Urgent 3-pulse cadence alternating between 784 Hz and 1046 Hz.
- **Critical**: 900–1350 Hz warbling emergency siren cycling until silenced or acknowledged.
- Inactive responders and users with audible notifications disabled receive visual notifications only.
