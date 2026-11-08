# GERMS: Gudo Emergency Response Management System
Java 25 + Swing + FlatLaf desktop client, Supabase backend (Auth, PostgreSQL + RLS, Realtime, Storage).
Emergency Responder Management System primarily intended for Red Cross emergency response operations (Davao City Chapter).

## Setup
1. **Supabase project**: create one at supabase.com.
2. **Migrations**: in the SQL editor run, in order:
   - `database/migrations/001_core_schema.sql`
   - `database/migrations/002_rls_policies.sql`
   - `database/migrations/003_rpc_functions.sql`
   - `database/migrations/004_reference_seed.sql`
   - `database/migrations/005_response_teams.sql`
   - `database/migrations/006_operations_resources.sql`
   - `database/migrations/007_admin_management.sql`
   - `database/migrations/008_responder_command.sql`
   - `database/migrations/009_phase3_operations.sql` (Phase 3 Complete Operations, Teams, Resources, SITREPs, Comms, Alarms)
   - `database/migrations/010_fix_auth_registration_search_path.sql` (Fix Auth signup trigger type resolution)
   - `database/migrations/011_fix_profile_relationships.sql` (Disambiguate profile embeds and expose audit actors)
3. **First admin**: follow the bootstrap comments at the top of `database/seed_data.sql`.
4. **Client config**: copy `src/main/resources/application.properties.example` to `application.properties` in the project root, or set `GERMS_SUPABASE_URL` and `GERMS_SUPABASE_ANON_KEY`. Use the **anon/publishable** key only. The app refuses service-role or secret keys.
5. **Run**: `mvn exec:java` (JDK 25).
6. **Package**: `mvn package`, then `java -jar target/germs-0.1.0-SNAPSHOT.jar` (run from the folder holding `application.properties`).
7. **Tests**: `mvn test`.

## Operational Roles & Workflows

### Guest / Public User
- Register for an account or explore via guest mode.
- Submit emergency incident reports (enters verification queue).
- View submitted incident reports and live verification progress.
- Access public emergency announcements and weather warnings.
- Browse public evacuation centers and emergency hotline directories.

### Responder
- Access authenticated responder dashboard.
- Live Incident Feed with category, severity, and interactive map views.
- Receive persistent emergency alerts and severity-based audible alarms (Low, Moderate, High, Critical) with alarm silencing and acknowledgement.
- Accept, decline, or leave incident deployment requests pending.
- Advance assignment status (`assigned -> preparing -> en_route -> arrived -> on_task -> completed`).
- Opt-in live GPS / operational coordinate tracking to Command Center.
- View squad roster and assigned team leaders.
- Submit Field Situation Reports (SITREP) with casualty estimates, field conditions, and medical/resource needs.
- Coordinate via dedicated incident tactical communication channels.

### Administrator
- Centralized Incident Command Dashboard.
- Verify incoming public reports, publish incidents, update severity, and pinpoint locations via interactive OpenStreetMap.
- Full Dispatch Center: review responder responses, confirm assignments with conflict prevention and emergency override, cancel assignments, and resolve operations.
- User & Responder Management: promote guest accounts to Responder with classifications (Search and Rescue, EMT, Flood Response, etc.) and availability control.
- Team & Squad Management: create response teams, designate squad leaders, and assign teams to missions.
- Resource & Equipment Management: track inventory (medical equipment, ambulances, rescue gear), allocate equipment to incidents atomically, and release upon operation closure.
- Evacuation Center Management: monitor shelter capacities, update occupancy, and manage operating statuses.
- Incident Communication Channel: broadcast announcements and monitor mission threads.
- Operational Analytics & Reporting: aggregate metrics by severity and category, and export CSV operational reports.
- Append-only activity & audit logs.

## Security Architecture
- Clients have no direct write access to incidents, assignments, audit logs, or resources. All privileged writes execute via `SECURITY DEFINER` RPCs that enforce server-side RBAC and RLS policies.
- Role promotion and user management are authorized server-side.
- Zero service-role credentials in client code: client runs strictly using public anon/publishable keys with authenticated JWTs.
