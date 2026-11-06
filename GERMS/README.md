# GERMS: Gudo Emergency Response Management System
Java 25 + Swing + FlatLaf desktop client, Supabase backend (Auth, PostgreSQL + RLS, Realtime, Storage).
Student project for the Philippine Red Cross, Davao City Chapter. Not endorsed by the organization. All sample data is synthetic.

## Setup
1. **Supabase project**: create one at supabase.com.
2. **Migrations**: in the SQL editor run, in order, `database/migrations/001_core_schema.sql`, `002_rls_policies.sql`, `003_rpc_functions.sql`, `004_reference_seed.sql`, `005_response_teams.sql`, `006_operations_resources.sql`, `007_admin_management.sql`, and `008_responder_command.sql`.
3. **First admin**: follow the bootstrap comments at the top of `database/seed_data.sql`. The client and setup scripts do not seed sample incidents.
4. **Client config**: copy `src/main/resources/application.properties.example` to `application.properties` in the project root (git-ignored), or set `GERMS_SUPABASE_URL` and `GERMS_SUPABASE_ANON_KEY`. Use the **anon/publishable** key only. The app refuses service-role or secret keys.
5. **Run**: `mvn exec:java` (JDK 25). NetBeans: open as Maven project, Run. VS Code: install "Extension Pack for Java", open the folder, run `Main`.
6. **Package**: `mvn package`, then `java -jar target/germs-0.1.0-SNAPSHOT.jar` (run from the folder holding `application.properties`).
7. **Tests**: `mvn test`.

## Security notes
- Clients have no direct write access to incidents, assignments, or audit logs. All privileged writes are `SECURITY DEFINER` RPCs in migration 003 that re-check the caller's role.
- User creation is performed by the `supabase/functions/admin-create-user` Edge Function using the service role only on the server. Deploy it with `supabase functions deploy admin-create-user`; never put its service-role key in the desktop app.

### Deploy account management

The **Add account** action requires the Edge Function to be deployed to the same Supabase project configured in `application.properties`.
Run these commands from the repository root (`GERMS-ERS`), not from the `GERMS` Maven folder:

```powershell
supabase login
supabase link --project-ref <your-project-ref>
supabase functions deploy admin-create-user
```

The function uses Supabase's server-managed `SUPABASE_SERVICE_ROLE_KEY`; do not copy that key into the desktop application or commit it to source control. After deployment, sign out and sign back in to GERMS, then use **User Management > Add account**.
- `profiles.role` and `is_active` are not client-updatable (column-level grants).
- `public_advisories` is a view owned by the migration role and intentionally bypasses RLS to expose a fixed, narrow column set to guests. Review it before deployment.
- Guest report spam control (rate limiting / CAPTCHA) is not implemented; add it before any public exposure.

## Current implementation status

This repository contains the Phase 2 incident command workflows plus live Supabase-backed operations views for incidents, locations, responder and user directories, reports, evacuation centers, and disaster resources. Account creation and privilege changes remain server-authorized through the planned Edge Function; the desktop client never handles service-role credentials. See [implementation status](docs/implementation-status.md), [architecture](ARCHITECTURE.md), and [testing notes](TESTING.md).
