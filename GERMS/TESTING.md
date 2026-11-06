# Testing and validation

## Automated checks

Run `mvn test` with JDK 25. The existing automated suite covers access policy behavior. This workspace does not include a configured live Supabase project, so Postgres migrations, Auth, RLS, and RPC integration must be validated in a non-production Supabase project.

## Dispatch workflow checks

With migrations 001-005 applied and an administrator session:

1. Publish an incident with eligible responders; confirm response notifications exist in the database.
2. Sign in as a responder and record an accepted or declined response using the current supported responder workflow/database RPC.
3. Open Deployment and Dispatch; confirm response totals and rows refresh while visible.
4. Select an accepted response and confirm assignment; verify the database assignment, notification, and audit entry.
5. Attempt a duplicate or conflicting assignment and confirm the RPC rejects it.
6. Sign in without administrator authorization and verify privileged RPCs reject access.

The complete Phase 2 end-to-end matrix remains outstanding, especially responder response UI, notification delivery, availability, account/classification management, and network interruption behavior.
