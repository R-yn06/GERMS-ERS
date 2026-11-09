# Testing and Validation

## Automated Unit Testing
Run the complete automated test suite with:
```bash
mvn test
```
The automated test suite covers:
- `IncidentLifecycleTest`: IncidentBuilder validations, coordinates and description persistence, State pattern lifecycle transitions, blocker rules (requiring team before mobilizing), and alias status parsing.
- `OperationalModelTest`: Profile creation and JSON parsing, Role database parsing with fallback, guest profile generation, ResponseTeam model integrity.
- `AccessPolicyTest`: Strict role-to-module mapping for Admin, Responder, and Guest; rejection of privileged keys.
- `AlarmServiceTest`: Singleton integrity, muting state management, audible tone deduplication, and active responder / preference filtering.

## Manual Operational Verification Scenarios

### 1. User Roles & Account Workflow
- **Registration**: On the Login screen, click "Create new account (Register)". Fill in Full Name, Email, Phone, and Password. Verify account receives Guest role by default.
- **Role Promotion**: Sign in as Administrator. Navigate to **User & Responder Management**. Locate the guest account, click **Promote to Responder**, select a classification (e.g. `DMS_SAR - Search and Rescue`), and confirm. Verify role updates to `responder` and availability initializes to `AVAILABLE`.
- **Sign In as Responder**: Sign in with the promoted credentials. Verify access to Responder Dashboard, Incident Feed, Live Map, Notifications, My Deployments, and SITREPs.

### 2. Incident Command & Interactive Mapping
- **Incident Pinning**: Navigate to **Incident Management**, click **+ Report incident**. Enter title, category, severity, and coordinates (e.g., Davao SPMC 7.0988, 125.6267). Confirm creation.
- **GIS Rendering**: Open the **Interactive Map** tab in Incident details or open **Live Operations Map**. Verify the incident pin appears in red at the exact geographic coordinates.
- **Publication & Notifications**: As Administrator, advance or publish the incident. Verify notification records are created for eligible active responders.

### 3. Emergency Alarms & Notification Center
- **Alarm Triggering**: When an incident is published with `CRITICAL` severity, verify active responders receive the audible warbling siren.
- **Alarm Dismissal**: Click **Silence / Dismiss Alarm** or **Acknowledge Selected** in **Notifications & Emergency Alarms**. Verify audio ceases immediately.
- **Muting Toggle**: Toggle **Enable Audible Alarms** off. Trigger a test alarm; verify audio line remains muted. Test each tone (Low chime, Moderate alert, High cadence, Critical siren) via the test buttons.

### 4. Dispatch Center & Deployments
- **Response**: As Responder, open an incident in the feed and click **Accept response**.
- **Confirmation**: As Administrator, open **Dispatch Center**. Verify the accepted responder appears. Select the row and click **Confirm Assignment**.
- **Conflict Prevention**: Attempt to confirm the same responder to another active incident without checking the emergency override checkbox; verify assignment is blocked. Check the override box and verify assignment succeeds.
- **Mission Progress**: As Responder, open **My Deployments**. Advance status: `assigned -> preparing -> en_route -> arrived -> on_task -> completed`.

### 5. Live Responder GPS Location Tracking
- As Responder, open **My Deployments** and check **Opt-in: Share My Operational Location with Command Center**. Enter coordinates (or transmit heartbeat).
- As Administrator, open **Live Operations Map**. Verify the blue responder dot appears with the responder's name and last reported timestamp.

### 6. Emergency Resources & Equipment
- Open **Resource & Equipment Inventory**, click **+ Add Resource** (e.g. "Ambulance Van #3", category `ambulance`, quantity 2).
- Click **Allocate to Incident**, select an active incident, and allocate 1 unit. Verify available inventory drops to 1 atomically and allocation appears in history.
- Select the allocation and click **Release Allocated** (Return to Inventory). Verify available quantity restores to 2.

### 7. Evacuation Centers & Shelters
- Open **Evacuation Centers**. Click **+ Register Shelter** with capacity (e.g. 500).
- Click **Update Occupancy** (e.g. set to 250). Verify remaining space updates to 250.

### 8. Field Situation Reporting (SITREP) & Emergency Comms
- As deployed Responder, open **Field Situation Reports**, click **+ Submit Field SITREP**. Mark as Urgent with casualty count and medical needs. Verify report logs to chronological SITREP timeline and triggers notification.
- Open **Emergency Communication**, select the incident channel, and transmit broadcast messages. Verify messages display with timestamp and role badge.
