# Setup Guide for GERMS

## Prerequisites
- Java Development Kit (JDK 25 or later).
- Apache Maven (3.9+).
- A Supabase project (at [supabase.com](https://supabase.com)).

## Database Initialization
In the Supabase SQL Editor, execute the SQL migration scripts in exact numerical order:
1. `database/migrations/001_core_schema.sql`
2. `database/migrations/002_rls_policies.sql`
3. `database/migrations/003_rpc_functions.sql`
4. `database/migrations/004_reference_seed.sql`
5. `database/migrations/005_response_teams.sql`
6. `database/migrations/006_operations_resources.sql`
7. `database/migrations/007_admin_management.sql`
8. `database/migrations/008_responder_command.sql`
9. `database/migrations/009_phase3_operations.sql`
10. `database/migrations/010_fix_auth_registration_search_path.sql`
11. `database/migrations/011_fix_profile_relationships.sql`

## First Administrator Account
1. In Supabase Dashboard, go to **Authentication > Users** and click **Add User**.
2. Supply your administrator email and a secure password. Auto-confirm user.
3. In SQL Editor, run:
```sql
INSERT INTO public.profiles (id, full_name, email, role, is_active)
VALUES ('<PASTE-USER-UUID-HERE>', 'Red Cross Commander', 'admin@example.com', 'admin', true)
ON CONFLICT (id) DO UPDATE SET role = 'admin', is_active = true;
```

## Client Configuration
Create `application.properties` in the project root (`GERMS/application.properties`) or set environment variables:
```properties
supabase.url=https://<your-project-ref>.supabase.co
supabase.anonKey=<your-anon-publishable-key>
```
> **Security Note:** Use the public `anon` / publishable key only. The application intentionally detects and rejects privileged service-role JWTs or secret keys (`sb_secret_...`) to protect database security.

## Building and Running
```bash
# Run unit tests
mvn test

# Run desktop client
mvn exec:java

# Build self-contained executable JAR
mvn package
java -jar target/germs-0.1.0-SNAPSHOT.jar
```
