# GERMS: Gudo Emergency Response Management System
Java 21 + Swing + FlatLaf desktop client, Supabase backend (Auth, PostgreSQL + RLS, Realtime, Storage).
Student project for the Philippine Red Cross, Davao City Chapter. Not endorsed by the organization. All sample data is synthetic.

## Setup
1. **Supabase project**: create one at supabase.com.
2. **Migrations**: in the SQL editor run, in order, `database/migrations/001_core_schema.sql`, `002_rls_policies.sql`, `003_rpc_functions.sql`, `004_reference_seed.sql`, and `005_response_teams.sql`.
3. **First admin**: follow the bootstrap comments at the top of `database/seed_data.sql`. The client and setup scripts do not seed sample incidents.
4. **Client config**: copy `src/main/resources/application.properties.example` to `application.properties` in the project root (git-ignored), or set `GERMS_SUPABASE_URL` and `GERMS_SUPABASE_ANON_KEY`. Use the **anon/publishable** key only. The app refuses service-role or secret keys.
5. **Run**: `mvn exec:java` (JDK 21). NetBeans: open as Maven project, Run. VS Code: install "Extension Pack for Java", open the folder, run `Main`.
6. **Package**: `mvn package`, then `java -jar target/germs-0.1.0-SNAPSHOT.jar` (run from the folder holding `application.properties`).
7. **Tests**: `mvn test`.

## Security notes
- Clients have no direct write access to incidents, assignments, or audit logs. All privileged writes are `SECURITY DEFINER` RPCs in migration 003 that re-check the caller's role.
- `profiles.role` and `is_active` are not client-updatable (column-level grants).
- `public_advisories` is a view owned by the migration role and intentionally bypasses RLS to expose a fixed, narrow column set to guests. Review it before deployment.
- Guest report spam control (rate limiting / CAPTCHA) is not implemented; add it before any public exposure.
