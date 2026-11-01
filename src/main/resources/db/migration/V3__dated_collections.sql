-- One transactional cutover. Mapping is trusted operator-supplied migration input, not client JSON.
LOCK TABLE users, wards, streets, operator_wards, collection_schedules, pickups, reports IN SHARE ROW EXCLUSIVE MODE;
CREATE TEMP TABLE collection_cutover_input ON COMMIT DROP AS
SELECT coalesce(nullif('${collectionCutoverDate}','AUTO')::date,
       (CURRENT_TIMESTAMP AT TIME ZONE 'Africa/Lagos')::date) AS cutover_date;
CREATE TEMP TABLE collection_operator_mapping (
 street_id uuid NOT NULL, collection_date date, operator_id uuid NOT NULL,
 UNIQUE NULLS NOT DISTINCT (street_id,collection_date)
) ON COMMIT DROP;
INSERT INTO collection_operator_mapping
SELECT "streetId", "collectionDate", "operatorId"
FROM jsonb_to_recordset('${collectionMigrationMapping}'::jsonb)
 AS m("streetId" uuid,"collectionDate" date,"operatorId" uuid);

DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM collection_operator_mapping m
   WHERE NOT EXISTS (SELECT 1 FROM streets s JOIN operator_wards ow ON ow.ward_id=s.ward_id
     JOIN users u ON u.id=ow.operator_id AND u.role='OPERATOR'
     WHERE s.id=m.street_id AND u.id=m.operator_id)) THEN
  RAISE EXCEPTION 'V3 preflight: invalid explicit operator/ward mapping';
 END IF;
 IF EXISTS (SELECT 1 FROM collection_schedules cs JOIN streets s ON s.id=cs.street_id
   JOIN operator_wards ow ON ow.ward_id=s.ward_id JOIN users u ON u.id=ow.operator_id
   WHERE cs.active AND u.role<>'OPERATOR') THEN
  RAISE EXCEPTION 'V3 preflight: invalid active ward assignment; explicit remediation required';
 END IF;
 IF EXISTS (SELECT 1 FROM pickups p JOIN streets s ON s.id=p.street_id WHERE p.status='COMPLETED' AND
   (p.pickup_date > (CURRENT_TIMESTAMP AT TIME ZONE 'Africa/Lagos')::date OR NOT EXISTS
    (SELECT 1 FROM users u JOIN operator_wards ow ON ow.operator_id=u.id
     WHERE u.id=p.recorded_by AND u.role='OPERATOR' AND ow.ward_id=s.ward_id))) THEN
  RAISE EXCEPTION 'V3 preflight: invalid historical completion operator/ward or future date; explicit remediation required';
 END IF;
 IF EXISTS (SELECT 1 FROM reports WHERE report_type='MISSED_PICKUP'
    AND collection_date >= (CURRENT_TIMESTAMP AT TIME ZONE 'Africa/Lagos')::date) THEN
  RAISE EXCEPTION 'V3 preflight: missed-pickup evidence must reference an elapsed date';
 END IF;
END $$;

CREATE TEMP TABLE collection_occurrences (
 id uuid PRIMARY KEY, street_id uuid NOT NULL, operator_id uuid NOT NULL,
 collection_date date NOT NULL, created_at timestamptz NOT NULL, UNIQUE(street_id,collection_date)
) ON COMMIT DROP;
-- Only dates with actual COMPLETED facts are reconstructed historically.
INSERT INTO collection_occurrences
SELECT md5('historical-completion:'||p.id::text)::uuid,p.street_id,p.recorded_by,p.pickup_date,p.created_at
FROM pickups p WHERE p.status='COMPLETED';

CREATE TEMP TABLE collection_weekly_expansion ON COMMIT DROP AS
SELECT md5('weekly-occurrence:'||cs.id::text||':'||d::date::text)::uuid AS id,cs.street_id,
 coalesce(exact.operator_id,default_mapping.operator_id,
   CASE WHEN candidates.n=1 THEN candidates.ids[1] END) AS operator_id,
 d::date AS collection_date,CURRENT_TIMESTAMP AS created_at
FROM collection_schedules cs JOIN streets s ON s.id=cs.street_id
CROSS JOIN collection_cutover_input input
CROSS JOIN LATERAL generate_series(input.cutover_date::timestamp,(input.cutover_date+29)::timestamp,interval '1 day') d
LEFT JOIN collection_operator_mapping exact ON exact.street_id=cs.street_id AND exact.collection_date=d::date
LEFT JOIN collection_operator_mapping default_mapping ON default_mapping.street_id=cs.street_id AND default_mapping.collection_date IS NULL
LEFT JOIN LATERAL (SELECT count(*) n,array_agg(u.id) ids FROM operator_wards ow JOIN users u
 ON u.id=ow.operator_id AND u.role='OPERATOR' WHERE ow.ward_id=s.ward_id) candidates ON true
WHERE cs.active AND cs.scheduled_day=extract(isodow FROM d);

DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM collection_weekly_expansion WHERE operator_id IS NULL) THEN
  RAISE EXCEPTION 'V3 preflight: active weekly schedules require an explicit mapping for wards with zero or multiple eligible operators';
 END IF;
 IF EXISTS (SELECT 1 FROM collection_weekly_expansion w JOIN collection_occurrences h USING(street_id,collection_date)
    WHERE w.operator_id<>h.operator_id) OR EXISTS
   (SELECT 1 FROM collection_operator_mapping m JOIN collection_occurrences h ON h.street_id=m.street_id
     AND m.collection_date=h.collection_date
    WHERE m.operator_id<>h.operator_id) THEN
  RAISE EXCEPTION 'V3 preflight: cutover mapping conflicts with the recorded historical operator';
 END IF;
END $$;
INSERT INTO collection_occurrences SELECT * FROM collection_weekly_expansion ON CONFLICT(street_id,collection_date) DO NOTHING;
-- Explicit dated mappings may supply prerequisite occurrences for actual pre-existing reports only.
INSERT INTO collection_occurrences
SELECT md5('mapped-occurrence:'||m.street_id::text||':'||m.collection_date::text)::uuid,
 m.street_id,m.operator_id,m.collection_date,CURRENT_TIMESTAMP
FROM collection_operator_mapping m WHERE m.collection_date IS NOT NULL AND EXISTS
 (SELECT 1 FROM reports r WHERE r.report_type='MISSED_PICKUP' AND r.street_id=m.street_id AND r.collection_date=m.collection_date)
ON CONFLICT(street_id,collection_date) DO NOTHING;
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM collection_operator_mapping m JOIN collection_occurrences o USING(street_id,collection_date)
   WHERE m.operator_id<>o.operator_id) THEN
  RAISE EXCEPTION 'V3 preflight: explicit dated mapping conflicts with an occurrence';
 END IF;
 IF EXISTS (SELECT 1 FROM collection_operator_mapping m WHERE m.collection_date IS NOT NULL AND NOT EXISTS
    (SELECT 1 FROM collection_occurrences o WHERE o.street_id=m.street_id AND o.collection_date=m.collection_date)) THEN
  RAISE EXCEPTION 'V3 preflight: unused dated mapping; only cutover dates or actual historical evidence may define occurrences';
 END IF;
 IF EXISTS (SELECT 1 FROM reports r WHERE r.report_type='MISSED_PICKUP' AND NOT EXISTS
    (SELECT 1 FROM collection_occurrences o WHERE o.street_id=r.street_id AND o.collection_date=r.collection_date)) THEN
  RAISE EXCEPTION 'V3 preflight: existing missed-pickup reports need explicit dated occurrence mappings; no history will be invented';
 END IF;
END $$;

ALTER TABLE collection_schedules RENAME TO legacy_collection_schedules;
ALTER TABLE legacy_collection_schedules RENAME CONSTRAINT collection_schedules_pkey TO legacy_collection_schedules_pkey;
ALTER TABLE pickups RENAME TO legacy_pickups;
CREATE TABLE collection_migration_cutover (
 id smallint PRIMARY KEY CHECK(id=1),cutover_date date NOT NULL,horizon_end_date date NOT NULL,
 recorded_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO collection_migration_cutover(id,cutover_date,horizon_end_date) SELECT 1,cutover_date,cutover_date+29 FROM collection_cutover_input;
CREATE TABLE collection_schedules (
 id uuid PRIMARY KEY,street_id uuid NOT NULL REFERENCES streets(id),operator_id uuid NOT NULL REFERENCES users(id),
 collection_date date NOT NULL,created_at timestamptz NOT NULL DEFAULT now(),
 CONSTRAINT uq_schedule_street_date UNIQUE(street_id,collection_date)
);
INSERT INTO collection_schedules SELECT * FROM collection_occurrences;
CREATE TABLE collections (
 id uuid PRIMARY KEY,street_id uuid NOT NULL REFERENCES streets(id),collection_date date NOT NULL,
 completed_by uuid NOT NULL REFERENCES users(id),completed_at timestamptz NOT NULL,
 CONSTRAINT ck_collection_not_future CHECK(collection_date <= (CURRENT_TIMESTAMP AT TIME ZONE 'Africa/Lagos')::date),
 CONSTRAINT uq_collection_street_date UNIQUE(street_id,collection_date),
 CONSTRAINT fk_collection_schedule FOREIGN KEY(street_id,collection_date) REFERENCES collection_schedules(street_id,collection_date)
);
INSERT INTO collections(id,street_id,collection_date,completed_by,completed_at)
SELECT id,street_id,pickup_date,recorded_by,created_at FROM legacy_pickups WHERE status='COMPLETED';
ALTER TABLE reports ADD CONSTRAINT fk_report_collection_schedule FOREIGN KEY(street_id,collection_date)
 REFERENCES collection_schedules(street_id,collection_date);
ALTER TABLE reports ADD CONSTRAINT ck_report_elapsed_collection_date CHECK
 (collection_date IS NULL OR collection_date < (CURRENT_TIMESTAMP AT TIME ZONE 'Africa/Lagos')::date);
