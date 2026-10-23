-- GERMS 004: reference data (not sample data). Safe to re-run.
insert into service_divisions(code,name) values
 ('DMS','Disaster Management Services'),('SAFETY','Safety Services'),('VOLUNTEER','Volunteer Services (Red Cross 143 / Youth)'),
 ('BLOOD','Blood Services'),('WELFARE','Health and Welfare Services'),('TRAINING','Safety Training and Certification')
on conflict (code) do nothing;

insert into responder_classifications(division_id, code, name, requires_certification)
select d.id, c.code, c.name, c.cert from service_divisions d join (values
 ('DMS','DMS_SAR','Search and Rescue',true),('DMS','DMS_FLOOD','Flood and Landslide Response',false),('DMS','DMS_FIRE','Typhoon and Fire Response',false),
 ('DMS','DMS_ASSESS','Disaster Assessment',false),('DMS','DMS_EVAC','Evacuation Operations',false),('DMS','DMS_RELIEF','Relief Logistics',false),
 ('SAFETY','SAF_ERU','Emergency Response Unit',true),('SAFETY','SAF_EMR','Emergency Medical Responder',true),('SAFETY','SAF_EMT','Emergency Medical Technician',true),
 ('SAFETY','SAF_AMB','Ambulance Crew',true),('SAFETY','SAF_FA','First Aid Team',true),
 ('VOLUNTEER','VOL_143L','Red Cross 143 Leader',false),('VOLUNTEER','VOL_143V','Red Cross 143 Volunteer',false),('VOLUNTEER','VOL_YOUTH','Youth Volunteer',false)
) as c(div_code, code, name, cert) on c.div_code = d.code
on conflict (code) do nothing;

insert into certifications(code,name,validity_months) values
 ('FIRST_AID','Standard First Aid',36),('CPR_AED','CPR and AED',24),('BLS','Basic Life Support',24),('ALS','Advanced Life Support',24),
 ('WATER_SAFETY','Water Safety / Lifeguard',24),('OSH','Occupational Safety',36)
on conflict (code) do nothing;

insert into incident_command_positions(code,name,sort_order) values
 ('IC','Incident Commander',1),('OPS','Operations Section',2),('PLAN','Planning Section',3),('LOG','Logistics Section',4),
 ('FIN','Finance and Administration Section',5),('SO','Safety Officer',6),('TL','Team Leader',7)
on conflict (code) do nothing;

insert into incident_status_transitions(from_status,to_status) values
 ('draft','published'),('draft','archived'),('published','acknowledged'),('published','mobilizing'),('acknowledged','mobilizing'),
 ('mobilizing','active_response'),('active_response','stabilized'),('stabilized','active_response'),('stabilized','closed'),('closed','archived')
on conflict do nothing;

insert into assignment_status_transitions(from_status,to_status,responder_allowed) values
 ('assigned','preparing',true),('preparing','en_route',true),('en_route','arrived',true),('arrived','on_task',true),
 ('on_task','completed',true),('completed','released',false)
on conflict do nothing;

insert into system_settings(key,value,description) values
 ('one_active_assignment','true','A responder may hold only one active assignment across incidents'),
 ('realtime_poll_seconds','5','Fallback polling interval when Realtime is unavailable')
on conflict (key) do nothing;
