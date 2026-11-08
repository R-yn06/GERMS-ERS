-- GERMS 009: Phase 3 Emergency Operations, Incident Command, and Responder Management schema.
-- Run after migrations 001-008 in the Supabase SQL editor.

-- 1. Support guest user role in database enum
ALTER TYPE user_role ADD VALUE IF NOT EXISTS 'guest';

-- Allow registered users to create their own profile with guest role
GRANT INSERT ON profiles TO authenticated;
DO $$
BEGIN
  CREATE POLICY profiles_insert_self ON profiles
    FOR INSERT TO authenticated
    WITH CHECK (id = auth.uid() AND (role::text = 'guest' OR role::text = 'responder'));
EXCEPTION
  WHEN duplicate_object THEN null;
END $$;

-- Automatic trigger for auth.users creation
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS trigger AS $$
DECLARE
  v_name text;
  v_role public.user_role;
BEGIN
  -- Safe name calculation: fallback if missing or blank
  v_name := COALESCE(NULLIF(TRIM(NEW.raw_user_meta_data->>'full_name'), ''), split_part(COALESCE(NEW.email, 'user'), '@', 1));
  IF v_name IS NULL OR length(trim(v_name)) = 0 THEN
    v_name := 'User ' || substr(NEW.id::text, 1, 8);
  END IF;

  -- Safe role detection: prefer 'guest', fallback to 'responder'
  BEGIN
    v_role := 'guest'::public.user_role;
  EXCEPTION WHEN OTHERS THEN
    v_role := 'responder'::public.user_role;
  END;

  INSERT INTO public.profiles AS current_profile (id, full_name, email, phone, role, is_active)
  VALUES (
    NEW.id,
    v_name,
    NEW.email,
    NULLIF(TRIM(NEW.raw_user_meta_data->>'phone'), ''),
    v_role,
    true
  )
  ON CONFLICT (id) DO UPDATE SET
    full_name = CASE WHEN current_profile.full_name IS NULL OR current_profile.full_name = '' THEN EXCLUDED.full_name ELSE current_profile.full_name END,
    email = COALESCE(EXCLUDED.email, current_profile.email);

  RETURN NEW;
EXCEPTION WHEN OTHERS THEN
  -- Catch-all safeguard: NEVER let a profile creation failure abort auth.users creation
  RAISE WARNING 'handle_new_user failed for auth.user %: %', NEW.id, SQLERRM;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = '';

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- 2. Relax notifications.kind constraint to support phase 3 notification kinds
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_kind_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_kind_check
  CHECK (kind IN ('incident_published','assignment','status_change','announcement','deployment_request','field_report','incident_message','alarm'));

-- 3. Notification Preferences (per responder/user)
CREATE TABLE IF NOT EXISTS notification_preferences (
  profile_id uuid PRIMARY KEY REFERENCES profiles(id) ON DELETE CASCADE,
  audible_enabled boolean NOT NULL DEFAULT true,
  min_severity text NOT NULL DEFAULT 'low' CHECK (min_severity IN ('low','moderate','high','critical')),
  sound_repetition boolean NOT NULL DEFAULT true,
  desktop_push_enabled boolean NOT NULL DEFAULT true,
  updated_at timestamptz NOT NULL DEFAULT now()
);

-- 4. Responder Teams expansion & Team Members
ALTER TABLE response_teams ADD COLUMN IF NOT EXISTS leader_id uuid REFERENCES profiles(id);
ALTER TABLE response_teams ADD COLUMN IF NOT EXISTS description text;
ALTER TABLE response_teams ADD COLUMN IF NOT EXISTS contact_number text;
ALTER TABLE response_teams ADD COLUMN IF NOT EXISTS status text NOT NULL DEFAULT 'available'
  CHECK (status IN ('available','deployed','standby','offline'));

CREATE TABLE IF NOT EXISTS team_members (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  team_id uuid NOT NULL REFERENCES response_teams(id) ON DELETE CASCADE,
  profile_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  role_title text NOT NULL DEFAULT 'Member',
  assigned_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (team_id, profile_id)
);
CREATE INDEX IF NOT EXISTS idx_team_members_profile ON team_members(profile_id);

-- 5. Emergency Resources and Equipment Inventory
CREATE TABLE IF NOT EXISTS resources (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL CHECK (length(trim(name)) > 0),
  category text NOT NULL CHECK (category IN (
    'first_aid','medical_equipment','ambulance','rescue_equipment',
    'emergency_supplies','communication','evacuation','generator','vehicle','other'
  )),
  description text,
  quantity integer NOT NULL DEFAULT 1 CHECK (quantity >= 0),
  available_quantity integer NOT NULL DEFAULT 1 CHECK (available_quantity >= 0 AND available_quantity <= quantity),
  condition text NOT NULL DEFAULT 'good' CHECK (condition IN ('new','good','fair','needs_maintenance','damaged')),
  location text,
  status text NOT NULL DEFAULT 'available' CHECK (status IN (
    'available','reserved','deployed','under_maintenance','unavailable','retired'
  )),
  assigned_incident_id uuid REFERENCES incidents(id),
  responsible_personnel_id uuid REFERENCES profiles(id),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_resources_status ON resources(status);
CREATE INDEX IF NOT EXISTS idx_resources_category ON resources(category);

CREATE TABLE IF NOT EXISTS resource_allocations (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  resource_id uuid NOT NULL REFERENCES resources(id) ON DELETE CASCADE,
  incident_id uuid NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
  quantity integer NOT NULL DEFAULT 1 CHECK (quantity > 0),
  allocated_by uuid NOT NULL REFERENCES profiles(id),
  allocated_at timestamptz NOT NULL DEFAULT now(),
  released_at timestamptz,
  status text NOT NULL DEFAULT 'allocated' CHECK (status IN ('allocated','released','consumed')),
  notes text
);
CREATE INDEX IF NOT EXISTS idx_resource_allocations_incident ON resource_allocations(incident_id);

-- 6. Field Situation Reports
CREATE TABLE IF NOT EXISTS field_situation_reports (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  incident_id uuid NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
  reporter_id uuid NOT NULL REFERENCES profiles(id),
  team_id uuid REFERENCES response_teams(id),
  situation_description text NOT NULL CHECK (length(trim(situation_description)) > 0),
  field_conditions text,
  affected_individuals integer DEFAULT 0 CHECK (affected_individuals >= 0),
  casualties_count integer DEFAULT 0 CHECK (casualties_count >= 0),
  medical_needs text,
  resource_needs text,
  additional_observations text,
  latitude numeric(9,6) CHECK (latitude BETWEEN -90 AND 90),
  longitude numeric(9,6) CHECK (longitude BETWEEN -180 AND 180),
  urgent boolean NOT NULL DEFAULT false,
  admin_feedback text,
  reviewed_by uuid REFERENCES profiles(id),
  reviewed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_situation_reports_incident ON field_situation_reports(incident_id, created_at DESC);

-- 7. Incident Emergency Communication Messages
CREATE TABLE IF NOT EXISTS incident_messages (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  incident_id uuid NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
  sender_id uuid NOT NULL REFERENCES profiles(id),
  message text NOT NULL CHECK (length(trim(message)) > 0),
  message_type text NOT NULL DEFAULT 'regular' CHECK (message_type IN ('regular','announcement','team','urgent')),
  team_id uuid REFERENCES response_teams(id),
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_incident_messages_incident ON incident_messages(incident_id, created_at ASC);

-- 8. Emergency Contacts Directory
CREATE TABLE IF NOT EXISTS emergency_contacts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL CHECK (length(trim(name)) > 0),
  contact_number text NOT NULL CHECK (length(trim(contact_number)) > 0),
  organization text NOT NULL,
  address text,
  location text,
  category text NOT NULL DEFAULT 'Emergency Hotline',
  availability text DEFAULT '24/7',
  notes text,
  is_public boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

-- Seed Initial Contacts if empty
INSERT INTO emergency_contacts (name, contact_number, organization, address, location, category, availability, notes, is_public)
SELECT 'Davao City 911', '911 / (082) 295-3769', 'Central 911 Davao', 'Daang Maharlika, Davao City', 'Davao City Central', 'Emergency Hotline', '24/7', 'Primary emergency dispatch', true
WHERE NOT EXISTS (SELECT 1 FROM emergency_contacts WHERE contact_number LIKE '%911%');

INSERT INTO emergency_contacts (name, contact_number, organization, address, location, category, availability, notes, is_public)
SELECT 'Philippine Red Cross Davao City Chapter', '(082) 227-3932', 'Philippine Red Cross', 'Roxas Avenue, Davao City', 'Davao City', 'Rescue Organization', '24/7', 'Emergency ops & blood center', true
WHERE NOT EXISTS (SELECT 1 FROM emergency_contacts WHERE name LIKE '%Philippine Red Cross%');

INSERT INTO emergency_contacts (name, contact_number, organization, address, location, category, availability, notes, is_public)
SELECT 'Southern Philippines Medical Center (SPMC)', '(082) 227-2731', 'Department of Health', 'J.P. Laurel Ave, Bajada, Davao City', 'Bajada, Davao City', 'Hospital / Medical', '24/7', 'Tertiary government medical center', true
WHERE NOT EXISTS (SELECT 1 FROM emergency_contacts WHERE name LIKE '%SPMC%');

INSERT INTO emergency_contacts (name, contact_number, organization, address, location, category, availability, notes, is_public)
SELECT 'Davao City Disaster Risk Reduction Management Office (CDRRMO)', '(082) 295-3769', 'LGU Davao City', 'City Hall, Davao City', 'Davao City', 'LGU / Government', '24/7', 'Local government disaster coordinator', true
WHERE NOT EXISTS (SELECT 1 FROM emergency_contacts WHERE name LIKE '%CDRRMO%');

-- 9. Emergency Announcements and Public Advisories
CREATE TABLE IF NOT EXISTS emergency_announcements (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  title text NOT NULL CHECK (length(trim(title)) > 0),
  description text NOT NULL CHECK (length(trim(description)) > 0),
  priority text NOT NULL DEFAULT 'normal' CHECK (priority IN ('normal','high','critical')),
  related_incident_id uuid REFERENCES incidents(id),
  is_public boolean NOT NULL DEFAULT true,
  status text NOT NULL DEFAULT 'published' CHECK (status IN ('draft','published','expired','archived')),
  published_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz,
  created_by uuid REFERENCES profiles(id),
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_announcements_pub ON emergency_announcements(status, published_at DESC);

-- 10. Enable Row Level Security on all newly created tables
ALTER TABLE notification_preferences ENABLE ROW LEVEL SECURITY;
ALTER TABLE team_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE resources ENABLE ROW LEVEL SECURITY;
ALTER TABLE resource_allocations ENABLE ROW LEVEL SECURITY;
ALTER TABLE field_situation_reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE incident_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE emergency_contacts ENABLE ROW LEVEL SECURITY;
ALTER TABLE emergency_announcements ENABLE ROW LEVEL SECURITY;

-- Grants
GRANT SELECT ON notification_preferences, team_members, resources, resource_allocations,
                field_situation_reports, incident_messages, emergency_contacts, emergency_announcements
  TO authenticated;
GRANT SELECT ON emergency_contacts, emergency_announcements TO anon;

GRANT INSERT, UPDATE, DELETE ON notification_preferences, team_members, resources, resource_allocations,
                               field_situation_reports, incident_messages, emergency_contacts, emergency_announcements
  TO authenticated;

-- Policies:
-- Notification Preferences
CREATE POLICY notif_pref_own ON notification_preferences
  FOR ALL TO authenticated
  USING (profile_id = auth.uid() OR public.is_admin())
  WITH CHECK (profile_id = auth.uid() OR public.is_admin());

-- Team Members
CREATE POLICY team_members_read ON team_members FOR SELECT TO authenticated USING (true);
CREATE POLICY team_members_admin ON team_members FOR ALL TO authenticated
  USING (public.is_admin()) WITH CHECK (public.is_admin());

-- Resources
CREATE POLICY resources_read ON resources FOR SELECT TO authenticated USING (true);
CREATE POLICY resources_admin ON resources FOR ALL TO authenticated
  USING (public.is_admin()) WITH CHECK (public.is_admin());

-- Resource Allocations
CREATE POLICY resource_allocations_read ON resource_allocations FOR SELECT TO authenticated USING (true);
CREATE POLICY resource_allocations_admin ON resource_allocations FOR ALL TO authenticated
  USING (public.is_admin()) WITH CHECK (public.is_admin());

-- Field Situation Reports
CREATE POLICY field_reports_read ON field_situation_reports FOR SELECT TO authenticated
  USING (public.is_admin() OR reporter_id = auth.uid() OR public.can_view_incident(incident_id));
CREATE POLICY field_reports_insert ON field_situation_reports FOR INSERT TO authenticated
  WITH CHECK (reporter_id = auth.uid() AND (public.is_responder() OR public.is_admin()));
CREATE POLICY field_reports_admin_update ON field_situation_reports FOR UPDATE TO authenticated
  USING (public.is_admin() OR reporter_id = auth.uid()) WITH CHECK (public.is_admin() OR reporter_id = auth.uid());

-- Incident Messages
CREATE POLICY incident_messages_read ON incident_messages FOR SELECT TO authenticated
  USING (public.can_view_incident(incident_id));
CREATE POLICY incident_messages_insert ON incident_messages FOR INSERT TO authenticated
  WITH CHECK (sender_id = auth.uid() AND public.can_view_incident(incident_id));

-- Emergency Contacts
CREATE POLICY emergency_contacts_read_auth ON emergency_contacts FOR SELECT TO authenticated USING (true);
CREATE POLICY emergency_contacts_read_anon ON emergency_contacts FOR SELECT TO anon USING (is_public = true);
CREATE POLICY emergency_contacts_admin ON emergency_contacts FOR ALL TO authenticated
  USING (public.is_admin()) WITH CHECK (public.is_admin());

-- Emergency Announcements
CREATE POLICY announcements_read_auth ON emergency_announcements FOR SELECT TO authenticated USING (true);
CREATE POLICY announcements_read_anon ON emergency_announcements FOR SELECT TO anon USING (is_public = true AND status = 'published');
CREATE POLICY announcements_admin ON emergency_announcements FOR ALL TO authenticated
  USING (public.is_admin()) WITH CHECK (public.is_admin());

-- 11. RPC Functions for Phase 3 Operations

-- Promote Guest to Responder with classification assignment
CREATE OR REPLACE FUNCTION public.promote_to_responder(
  p_profile uuid,
  p_classification_code text,
  p_division_code text DEFAULT 'DMS'
) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  v_div uuid;
  v_cls uuid;
BEGIN
  PERFORM private.require_admin();
  
  IF NOT EXISTS (SELECT 1 FROM profiles WHERE id = p_profile) THEN
    RAISE EXCEPTION 'Target profile not found';
  END IF;

  -- Update role to responder
  UPDATE profiles SET role = 'responder', is_active = true WHERE id = p_profile;

  -- Assign division if provided
  IF coalesce(p_division_code, '') <> '' THEN
    SELECT id INTO v_div FROM service_divisions WHERE code = p_division_code;
    IF v_div IS NOT NULL THEN
      INSERT INTO profile_divisions (profile_id, division_id)
        VALUES (p_profile, v_div)
        ON CONFLICT DO NOTHING;
    END IF;
  END IF;

  -- Assign classification if provided
  IF coalesce(p_classification_code, '') <> '' THEN
    SELECT id INTO v_cls FROM responder_classifications WHERE code = p_classification_code;
    IF v_cls IS NOT NULL THEN
      INSERT INTO profile_classifications (profile_id, classification_id, verified_by, verified_at)
        VALUES (p_profile, v_cls, auth.uid(), now())
        ON CONFLICT (profile_id, classification_id)
        DO UPDATE SET verified_by = auth.uid(), verified_at = now();
    END IF;
  END IF;

  -- Initialize availability to available
  INSERT INTO responder_availability (profile_id, status, note, updated_at)
    VALUES (p_profile, 'available', 'Promoted to responder by command staff', now())
    ON CONFLICT (profile_id) DO UPDATE SET status = 'available', updated_at = now();

  PERFORM private.audit('profile.promote_responder', 'profile', p_profile::text,
    jsonb_build_object('role', 'responder', 'classification', p_classification_code, 'division', p_division_code));
END $$;

-- Allocate resource to incident atomically
CREATE OR REPLACE FUNCTION public.allocate_resource(
  p_incident uuid,
  p_resource uuid,
  p_quantity integer,
  p_notes text DEFAULT NULL
) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  v_res resources%rowtype;
  v_alloc_id uuid;
  v_inc_title text;
BEGIN
  PERFORM private.require_admin();

  IF p_quantity <= 0 THEN
    RAISE EXCEPTION 'Quantity must be greater than zero';
  END IF;

  SELECT title INTO v_inc_title FROM incidents WHERE id = p_incident;
  IF v_inc_title IS NULL THEN
    RAISE EXCEPTION 'Incident not found';
  END IF;

  SELECT * INTO v_res FROM resources WHERE id = p_resource FOR UPDATE;
  IF v_res.id IS NULL THEN
    RAISE EXCEPTION 'Resource not found';
  END IF;

  IF v_res.available_quantity < p_quantity THEN
    RAISE EXCEPTION 'Insufficient resource inventory. Available: %, Requested: %', v_res.available_quantity, p_quantity;
  END IF;

  -- Deduct inventory
  UPDATE resources
  SET available_quantity = available_quantity - p_quantity,
      status = CASE WHEN available_quantity - p_quantity = 0 THEN 'deployed' ELSE status END,
      updated_at = now()
  WHERE id = p_resource;

  -- Record allocation
  INSERT INTO resource_allocations (resource_id, incident_id, quantity, allocated_by, status, notes)
  VALUES (p_resource, p_incident, p_quantity, auth.uid(), 'allocated', p_notes)
  RETURNING id INTO v_alloc_id;

  -- Record in incident updates timeline
  INSERT INTO incident_updates (incident_id, author_id, kind, message)
  VALUES (p_incident, auth.uid(), 'note', 'Allocated resource: ' || p_quantity || 'x ' || v_res.name);

  PERFORM private.audit('resource.allocate', 'resource_allocation', v_alloc_id::text,
    jsonb_build_object('incident', p_incident, 'resource', p_resource, 'quantity', p_quantity));

  RETURN v_alloc_id;
END $$;

-- Release allocated resource
CREATE OR REPLACE FUNCTION public.release_resource(
  p_allocation uuid,
  p_consumed boolean DEFAULT false
) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  v_alloc resource_allocations%rowtype;
  v_res resources%rowtype;
BEGIN
  PERFORM private.require_admin();

  SELECT * INTO v_alloc FROM resource_allocations WHERE id = p_allocation FOR UPDATE;
  IF v_alloc.id IS NULL THEN
    RAISE EXCEPTION 'Allocation record not found';
  END IF;

  IF v_alloc.status <> 'allocated' THEN
    RAISE EXCEPTION 'Resource allocation is already %', v_alloc.status;
  END IF;

  SELECT * INTO v_res FROM resources WHERE id = v_alloc.resource_id FOR UPDATE;

  -- Update allocation
  UPDATE resource_allocations
  SET status = CASE WHEN p_consumed THEN 'consumed' ELSE 'released' END,
      released_at = now()
  WHERE id = p_allocation;

  -- If not consumed, restore available quantity
  IF NOT p_consumed THEN
    UPDATE resources
    SET available_quantity = LEAST(quantity, available_quantity + v_alloc.quantity),
        status = CASE WHEN available_quantity + v_alloc.quantity > 0 AND status = 'deployed' THEN 'available' ELSE status END,
        updated_at = now()
    WHERE id = v_alloc.resource_id;
  END IF;

  INSERT INTO incident_updates (incident_id, author_id, kind, message)
  VALUES (v_alloc.incident_id, auth.uid(), 'note',
    'Resource ' || (CASE WHEN p_consumed THEN 'marked consumed' ELSE 'released back to inventory' END) || ': ' || v_alloc.quantity || 'x ' || v_res.name);

  PERFORM private.audit('resource.release', 'resource_allocation', p_allocation::text,
    jsonb_build_object('consumed', p_consumed, 'quantity', v_alloc.quantity));
END $$;

-- Submit Field Situation Report
CREATE OR REPLACE FUNCTION public.submit_field_situation_report(
  p_incident uuid,
  p_description text,
  p_conditions text DEFAULT NULL,
  p_affected integer DEFAULT 0,
  p_casualties integer DEFAULT 0,
  p_medical text DEFAULT NULL,
  p_resources text DEFAULT NULL,
  p_observations text DEFAULT NULL,
  p_lat numeric DEFAULT NULL,
  p_lon numeric DEFAULT NULL,
  p_urgent boolean DEFAULT false
) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  v_id uuid;
  v_team_id uuid;
BEGIN
  IF NOT (public.is_responder() OR public.is_admin()) THEN
    RAISE EXCEPTION 'Only authorized responders or administrators can submit field reports' USING errcode = '42501';
  END IF;

  -- Get responder team if assigned
  SELECT tm.team_id INTO v_team_id
  FROM team_members tm
  WHERE tm.profile_id = auth.uid()
  LIMIT 1;

  INSERT INTO field_situation_reports (
    incident_id, reporter_id, team_id, situation_description, field_conditions,
    affected_individuals, casualties_count, medical_needs, resource_needs,
    additional_observations, latitude, longitude, urgent
  ) VALUES (
    p_incident, auth.uid(), v_team_id, p_description, p_conditions,
    coalesce(p_affected, 0), coalesce(p_casualties, 0), p_medical, p_resources,
    p_observations, p_lat, p_lon, coalesce(p_urgent, false)
  ) RETURNING id INTO v_id;

  -- Add to timeline
  INSERT INTO incident_updates (incident_id, author_id, kind, message)
  VALUES (p_incident, auth.uid(), 'field_update',
    (CASE WHEN p_urgent THEN '[URGENT SITREP] ' ELSE '[SITREP] ' END) || p_description);

  -- Notify admins if urgent
  IF p_urgent THEN
    INSERT INTO notifications (recipient_id, incident_id, kind, priority, title, body)
    SELECT p.id, p_incident, 'field_report', 'critical',
           'Urgent Situation Report',
           substr(p_description, 1, 150)
    FROM profiles p
    WHERE p.role = 'admin' AND p.is_active;
  END IF;

  PERFORM private.audit('field_report.submit', 'field_situation_report', v_id::text,
    jsonb_build_object('incident', p_incident, 'urgent', p_urgent));

  RETURN v_id;
END $$;

-- Send Incident Message
CREATE OR REPLACE FUNCTION public.send_incident_message(
  p_incident uuid,
  p_message text,
  p_type text DEFAULT 'regular',
  p_team uuid DEFAULT NULL
) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  v_id uuid;
BEGIN
  IF NOT public.can_view_incident(p_incident) THEN
    RAISE EXCEPTION 'Access to incident channel denied' USING errcode = '42501';
  END IF;

  INSERT INTO incident_messages (incident_id, sender_id, message, message_type, team_id)
  VALUES (p_incident, auth.uid(), p_message, coalesce(nullif(p_type,''),'regular'), p_team)
  RETURNING id INTO v_id;

  -- If announcement or urgent, notify assigned responders
  IF p_type IN ('announcement', 'urgent') THEN
    INSERT INTO notifications (recipient_id, incident_id, kind, priority, title, body)
    SELECT a.responder_id, p_incident, 'incident_message',
           CASE WHEN p_type = 'urgent' THEN 'high' ELSE 'normal' END,
           'Incident Broadcast: ' || (SELECT title FROM incidents WHERE id = p_incident),
           p_message
    FROM incident_assignments a
    WHERE a.incident_id = p_incident AND a.status NOT IN ('completed','released','cancelled')
      AND a.responder_id <> auth.uid();
  END IF;

  RETURN v_id;
END $$;

-- Opt-in Responder Location Update
CREATE OR REPLACE FUNCTION public.update_responder_location(
  p_lat numeric,
  p_lon numeric,
  p_accuracy numeric DEFAULT NULL,
  p_source text DEFAULT 'device',
  p_sharing boolean DEFAULT true
) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF NOT public.is_responder() THEN
    RAISE EXCEPTION 'Only responders may update operational location' USING errcode = '42501';
  END IF;

  INSERT INTO responder_locations (profile_id, latitude, longitude, accuracy_m, source, sharing_enabled, recorded_at)
  VALUES (auth.uid(), p_lat, p_lon, p_accuracy, coalesce(p_source,'device'), p_sharing, now())
  ON CONFLICT (profile_id) DO UPDATE SET
    latitude = excluded.latitude,
    longitude = excluded.longitude,
    accuracy_m = excluded.accuracy_m,
    source = excluded.source,
    sharing_enabled = excluded.sharing_enabled,
    recorded_at = now();
END $$;

-- Grant execution to authenticated
GRANT EXECUTE ON FUNCTION public.promote_to_responder(uuid, text, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.allocate_resource(uuid, uuid, integer, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.release_resource(uuid, boolean) TO authenticated;
GRANT EXECUTE ON FUNCTION public.submit_field_situation_report(uuid, text, text, integer, integer, text, text, text, numeric, numeric, boolean) TO authenticated;
GRANT EXECUTE ON FUNCTION public.send_incident_message(uuid, text, text, uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.update_responder_location(numeric, numeric, numeric, text, boolean) TO authenticated;

-- Add new tables to Realtime publication
DO $$
DECLARE
  t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['field_situation_reports','incident_messages','responder_locations','resources','resource_allocations','emergency_announcements','team_members']
  LOOP
    BEGIN
      EXECUTE format('ALTER PUBLICATION supabase_realtime ADD TABLE public.%I', t);
    EXCEPTION
      WHEN duplicate_object THEN NULL;
      WHEN undefined_object THEN RAISE NOTICE 'supabase_realtime publication not found';
    END;
  END LOOP;
END $$;
