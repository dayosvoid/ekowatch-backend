-- FICTIONAL demonstration only. Presentation: 2026-10-31T12:00:00Z (13:00 Africa/Lagos).
-- Coverage: 2026-10-16 through 2026-11-14 inclusive. Never use now() to renew evidence.
-- Insert-only: preserve existing UUIDs, user actions, preferences, credentials and evidence.
INSERT INTO wards(id,name,created_at)
VALUES ('10000000-0000-0000-0000-000000000001','Fictional Demo Lagoon Ward','2026-10-15T08:00:00Z')
ON CONFLICT DO NOTHING;

INSERT INTO streets(id,ward_id,name,latitude,longitude)
SELECT ('20000000-0000-0000-0000-'||lpad(n::text,12,'0'))::uuid,
 '10000000-0000-0000-0000-000000000001',name,
 CASE WHEN n=8 THEN NULL ELSE 6.4+n*0.001 END,CASE WHEN n=8 THEN NULL ELSE 3.3+n*0.001 END
FROM (VALUES
 (1,'Fictional Palm Street'),(2,'Fictional Bamboo Street'),(3,'Fictional Mangrove Street'),
 (4,'Fictional Canal Street'),(5,'Fictional Lagoon Street'),(6,'Fictional Market Street'),
 (7,'Fictional Demonstration Community Road beside the Imaginary Lagoon Environmental Learning Centre and Public Collection Meeting Point'),
 (8,'Fictional Quiet Street')) streets(n,name)
ON CONFLICT DO NOTHING;

-- Original users 1..5 retain their UUIDs/roles. Added resident 6, assigned operator 7, unassigned operator 8.
-- These values are deliberately NOT usable password-encoder hashes. Esther provisions credentials separately.
INSERT INTO users(id,email,password_hash,display_name,role,ward_id,street_id,created_at)
SELECT ('00000000-0000-0000-0000-'||lpad(n::text,12,'0'))::uuid,
 'demo'||n||'@example.invalid','!DEMO_LOGIN_DISABLED','Fictional Demo '||n,
 CASE WHEN n IN (4,7,8) THEN 'OPERATOR' WHEN n=5 THEN 'AUTHORITY' ELSE 'RESIDENT' END,
 CASE WHEN n IN (1,2,3,6) THEN '10000000-0000-0000-0000-000000000001'::uuid ELSE NULL END,
 CASE WHEN n IN (1,2,3,6) THEN ('20000000-0000-0000-0000-'||lpad(
   (CASE n WHEN 1 THEN 1 WHEN 2 THEN 2 WHEN 3 THEN 4 ELSE 7 END)::text,12,'0'))::uuid ELSE NULL END,
 '2026-10-15T08:00:00Z'::timestamptz
FROM generate_series(1,8) n ON CONFLICT DO NOTHING;
INSERT INTO operator_wards(operator_id,ward_id) VALUES
 ('00000000-0000-0000-0000-000000000004','10000000-0000-0000-0000-000000000001'),
 ('00000000-0000-0000-0000-000000000007','10000000-0000-0000-0000-000000000001')
ON CONFLICT DO NOTHING;

-- Preserve original seven schedule UUIDs on their explicit 31 October presentation dates.
INSERT INTO collection_schedules(id,street_id,operator_id,collection_date,created_at)
SELECT ('30000000-0000-0000-0000-'||lpad(n::text,12,'0'))::uuid,
 '20000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000004',
 DATE '2026-10-29'+n,'2026-10-15T08:00:00Z'::timestamptz
FROM generate_series(1,7) n ON CONFLICT DO NOTHING;

-- Patterns: streets 1/4/7/8 daily; 2/5 Mon/Wed/Fri; 3/6 Tue/Thu/Sat.
-- Operators 4 and 7 own streets 1..4 and 5..8 respectively. Dates are facts, not weekly mutable rules.
INSERT INTO collection_schedules(id,street_id,operator_id,collection_date,created_at)
SELECT ('31000000-0000-0000-0000-'||lpad((street*100+day_offset)::text,12,'0'))::uuid,
 ('20000000-0000-0000-0000-'||lpad(street::text,12,'0'))::uuid,
 ('00000000-0000-0000-0000-'||lpad((CASE WHEN street<=4 THEN 4 ELSE 7 END)::text,12,'0'))::uuid,
 DATE '2026-10-15'+day_offset,'2026-10-15T08:00:00Z'::timestamptz
FROM generate_series(1,8) street CROSS JOIN generate_series(1,30) day_offset
WHERE street IN (1,4,7,8)
 OR (street IN (2,5) AND extract(isodow FROM DATE '2026-10-15'+day_offset) IN (1,3,5))
 OR (street IN (3,6) AND extract(isodow FROM DATE '2026-10-15'+day_offset) IN (2,4,6))
ON CONFLICT DO NOTHING;

INSERT INTO collections(id,street_id,collection_date,completed_by,completed_at)
SELECT ('60000000-0000-0000-0000-'||lpad(n::text,12,'0'))::uuid,
 ('20000000-0000-0000-0000-'||lpad(street::text,12,'0'))::uuid,date,
 ('00000000-0000-0000-0000-'||lpad(operator_n::text,12,'0'))::uuid,completed_at::timestamptz
FROM (VALUES (1,1,DATE '2026-10-29',4,'2026-10-29T18:00:00Z'),
 (2,4,DATE '2026-10-30',4,'2026-10-30T18:00:00Z'),
 (3,7,DATE '2026-10-30',7,'2026-10-30T19:00:00Z')) facts(n,street,date,operator_n,completed_at)
ON CONFLICT DO NOTHING;

-- Original three blocked-drain IDs and 0/1/2 confirmation cases survive.
INSERT INTO reports(id,reporter_id,ward_id,street_id,report_type,note,created_at,updated_at)
SELECT ('40000000-0000-0000-0000-'||lpad(n::text,12,'0'))::uuid,
 '00000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001',
 '20000000-0000-0000-0000-000000000001','BLOCKED_DRAIN','Fictional blocked drain '||n,
 '2026-10-30T07:00:00Z'::timestamptz+n*interval '1 minute',
 '2026-10-30T07:00:00Z'::timestamptz+n*interval '1 minute'
FROM generate_series(1,3) n ON CONFLICT DO NOTHING;

-- All activity/evidence is fictional. No sanitised asset has been provisioned: all photo_id values stay null.
-- Report 8 explicitly demonstrates location denied and no photo; public locations are street coordinates only.
INSERT INTO reports(id,reporter_id,ward_id,street_id,report_type,note,collection_date,latitude,longitude,photo_id,created_at,updated_at)
SELECT ('40000000-0000-0000-0000-'||lpad(n::text,12,'0'))::uuid,
 ('00000000-0000-0000-0000-'||lpad(resident::text,12,'0'))::uuid,
 '10000000-0000-0000-0000-000000000001',
 ('20000000-0000-0000-0000-'||lpad(street::text,12,'0'))::uuid,
 type,note,date,NULL,NULL,NULL,created_at::timestamptz,created_at::timestamptz
FROM (VALUES
 (4,2,3,'FLOODED_ROAD','Fictional current flood testimony',NULL::date,'2026-10-31T10:00:00Z'),
 (5,3,5,'FLOODED_ROAD','Fictional expired flood testimony',NULL::date,'2026-10-30T15:00:00Z'),
 (6,6,2,'MISSED_PICKUP','Fictional missed collection testimony',DATE '2026-10-30','2026-10-31T08:00:00Z'),
 (7,1,4,'MISSED_PICKUP','Fictional testimony disputing a recorded completion',DATE '2026-10-30','2026-10-31T09:00:00Z'),
 (8,6,6,'ILLEGAL_DUMPING','Fictional dumping testimony; location permission denied; no photo',NULL::date,'2026-10-29T09:00:00Z'),
 (9,6,7,'FLOODED_ROAD','Fictional flood with newer resident evidence',NULL::date,'2026-10-30T01:00:00Z')
) evidence(n,resident,street,type,note,date,created_at)
ON CONFLICT DO NOTHING;

INSERT INTO report_confirmations(id,report_id,resident_id,confirmed_at)
SELECT ('50000000-0000-0000-0000-'||lpad(n::text,12,'0'))::uuid,
 ('40000000-0000-0000-0000-'||lpad((CASE WHEN n=1 THEN 2 ELSE 3 END)::text,12,'0'))::uuid,
 ('00000000-0000-0000-0000-'||lpad((CASE WHEN n=3 THEN 3 ELSE 2 END)::text,12,'0'))::uuid,
 '2026-10-30T08:00:00Z'::timestamptz+n*interval '1 minute'
FROM generate_series(1,3) n ON CONFLICT DO NOTHING;
INSERT INTO report_confirmations(id,report_id,resident_id,confirmed_at) VALUES
 ('50000000-0000-0000-0000-000000000004','40000000-0000-0000-0000-000000000009','00000000-0000-0000-0000-000000000002','2026-10-31T11:00:00Z'),
 ('50000000-0000-0000-0000-000000000005','40000000-0000-0000-0000-000000000009','00000000-0000-0000-0000-000000000003','2026-10-31T10:30:00Z')
ON CONFLICT DO NOTHING;
