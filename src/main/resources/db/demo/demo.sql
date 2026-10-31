INSERT INTO users(id,email,password_hash,display_name,role)
SELECT ('00000000-0000-0000-0000-'||lpad(n::text,12,'0'))::uuid,'demo'||n||'@example.invalid','!DEMO_LOGIN_DISABLED','Fictional Demo '||n,CASE WHEN n=4 THEN 'OPERATOR' WHEN n=5 THEN 'AUTHORITY' ELSE 'RESIDENT' END FROM generate_series(1,5) n ON CONFLICT DO NOTHING;
INSERT INTO wards(id,name) VALUES('10000000-0000-0000-0000-000000000001','Demo Lagoon Ward') ON CONFLICT DO NOTHING;
INSERT INTO streets(id,ward_id,name) VALUES('20000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001','Fictional Palm Street') ON CONFLICT DO NOTHING;
INSERT INTO operator_wards VALUES('00000000-0000-0000-0000-000000000004','10000000-0000-0000-0000-000000000001') ON CONFLICT DO NOTHING;
-- Seven stable demo occurrences anchored to the recorded cutover, not refreshed on repeat.
INSERT INTO collection_schedules(id,street_id,operator_id,collection_date) SELECT ('30000000-0000-0000-0000-'||lpad(n::text,12,'0'))::uuid,'20000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000004',cutover_date+n-2 FROM collection_migration_cutover CROSS JOIN generate_series(1,7) n ON CONFLICT DO NOTHING;
INSERT INTO reports(id,reporter_id,ward_id,street_id,report_type,note) SELECT ('40000000-0000-0000-0000-'||lpad(n::text,12,'0'))::uuid,'00000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001','BLOCKED_DRAIN','Fictional blocked drain '||n FROM generate_series(1,3) n ON CONFLICT DO NOTHING;
INSERT INTO report_confirmations(id,report_id,resident_id) SELECT ('50000000-0000-0000-0000-'||lpad(n::text,12,'0'))::uuid,('40000000-0000-0000-0000-'||lpad((CASE WHEN n=1 THEN 2 ELSE 3 END)::text,12,'0'))::uuid,('00000000-0000-0000-0000-'||lpad((CASE WHEN n=3 THEN 3 ELSE 2 END)::text,12,'0'))::uuid FROM generate_series(1,3) n ON CONFLICT DO NOTHING;

