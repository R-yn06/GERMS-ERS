# Setup

1. Install JDK 25 and Maven.
2. Create a Supabase project and apply `database/migrations/001_core_schema.sql` through `005_response_teams.sql` in order.
3. Bootstrap the first administrator following `database/seed_data.sql`.
4. Set `GERMS_SUPABASE_URL` and `GERMS_SUPABASE_ANON_KEY`, or create the ignored local `application.properties` described in `README.md`. Use only the anon/publishable key.
5. Run `mvn test`, then `mvn exec:java` from this directory.

Supabase Auth, Postgres, and PostgREST are required for authenticated workflows. Realtime publication configuration is described in `docs/implementation-status.md`. The client currently has no background push provider, audio alert resources, or account creation Edge Function. Do not expose the guest report form publicly until rate limiting or equivalent abuse controls are configured.
