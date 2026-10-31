-- Reject incompatible evidence before any DDL; never truncate or rewrite history.
DO $$
BEGIN
 IF EXISTS (SELECT 1 FROM reports WHERE char_length(description) NOT BETWEEN 1 AND 1000
            OR description <> btrim(description) OR btrim(description) = '') THEN
  RAISE EXCEPTION 'V2 preflight: incompatible report notes (must be trimmed, 1-1000 characters); explicit remediation required, no automatic truncation or rewriting';
 END IF;
END $$;

ALTER TABLE reports RENAME COLUMN description TO note;
ALTER TABLE reports ALTER COLUMN note TYPE varchar(1000);
ALTER TABLE reports ADD CONSTRAINT ck_report_note CHECK (char_length(note) BETWEEN 1 AND 1000 AND note = btrim(note));
ALTER TABLE reports DROP CONSTRAINT reports_report_type_check;
ALTER TABLE reports ADD CONSTRAINT ck_report_type CHECK (report_type IN ('MISSED_PICKUP','ILLEGAL_DUMPING','BLOCKED_DRAIN','FLOODED_ROAD'));

ALTER TABLE streets ADD COLUMN latitude double precision, ADD COLUMN longitude double precision;
ALTER TABLE streets ADD CONSTRAINT ck_street_coordinates CHECK (
 (latitude IS NULL AND longitude IS NULL) OR
 (latitude IS NOT NULL AND longitude IS NOT NULL AND latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180));

ALTER TABLE users ADD COLUMN ward_id uuid REFERENCES wards(id), ADD COLUMN street_id uuid;
ALTER TABLE users ADD CONSTRAINT ck_user_preferences CHECK ((ward_id IS NULL) = (street_id IS NULL));
ALTER TABLE users ADD CONSTRAINT fk_user_street_ward FOREIGN KEY (street_id,ward_id) REFERENCES streets(id,ward_id);

-- storage_key is supplied only by Esther's trusted sanitising adapter. No upload flow here.
CREATE TABLE photos (
 id uuid PRIMARY KEY,
 owner_id uuid NOT NULL REFERENCES users(id),
 storage_key text NOT NULL CHECK (btrim(storage_key) <> ''),
 content_type varchar(100) NOT NULL CHECK (btrim(content_type) <> ''),
 size_bytes bigint NOT NULL CHECK (size_bytes > 0),
 created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE reports ADD COLUMN photo_id uuid REFERENCES photos(id),
 ADD COLUMN latitude double precision, ADD COLUMN longitude double precision,
 ADD COLUMN collection_date date;
ALTER TABLE reports ADD CONSTRAINT ck_report_coordinates CHECK (
 (latitude IS NULL AND longitude IS NULL) OR
 (latitude IS NOT NULL AND longitude IS NOT NULL AND latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180));
ALTER TABLE reports ADD CONSTRAINT ck_report_collection_date CHECK (
 (report_type = 'MISSED_PICKUP' AND collection_date IS NOT NULL) OR
 (report_type <> 'MISSED_PICKUP' AND collection_date IS NULL));
-- Dated schedule FK follows in Step 4. Lifecycle columns/trigger stay untouched.
