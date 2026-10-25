CREATE TABLE users (
 id uuid PRIMARY KEY, email varchar(254) NOT NULL UNIQUE CHECK(email=lower(email)),
 password_hash varchar(255) NOT NULL, display_name varchar(100) NOT NULL,
 role varchar(20) NOT NULL CHECK(role IN ('RESIDENT','OPERATOR','AUTHORITY')), created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE wards(id uuid PRIMARY KEY,name varchar(100) NOT NULL UNIQUE,created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE streets(id uuid PRIMARY KEY,ward_id uuid NOT NULL REFERENCES wards(id),name varchar(150) NOT NULL,UNIQUE(ward_id,name),UNIQUE(id,ward_id));
CREATE TABLE operator_wards(operator_id uuid NOT NULL REFERENCES users(id),ward_id uuid NOT NULL REFERENCES wards(id),PRIMARY KEY(operator_id,ward_id));
CREATE TABLE collection_schedules(id uuid PRIMARY KEY,street_id uuid NOT NULL REFERENCES streets(id),scheduled_day smallint NOT NULL CHECK(scheduled_day BETWEEN 1 AND 7),active boolean NOT NULL DEFAULT true,UNIQUE(street_id,scheduled_day));
CREATE TABLE pickups(id uuid PRIMARY KEY,street_id uuid NOT NULL REFERENCES streets(id),pickup_date date NOT NULL,status varchar(20) NOT NULL CHECK(status IN ('COMPLETED','FAILED')),recorded_by uuid NOT NULL REFERENCES users(id),created_at timestamptz NOT NULL DEFAULT now(),CONSTRAINT uq_pickup_street_date UNIQUE(street_id,pickup_date));
CREATE TABLE reports(id uuid PRIMARY KEY,reporter_id uuid NOT NULL REFERENCES users(id),ward_id uuid NOT NULL REFERENCES wards(id),street_id uuid NOT NULL,report_type varchar(30) NOT NULL CHECK(report_type='BLOCKED_DRAIN'),description varchar(2000) NOT NULL CHECK(length(trim(description))>0),status varchar(20) NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','RESOLVED','REJECTED')),created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now(),FOREIGN KEY(street_id,ward_id) REFERENCES streets(id,ward_id));
CREATE TABLE confirmations(id uuid PRIMARY KEY,report_id uuid NOT NULL REFERENCES reports(id),user_id uuid NOT NULL REFERENCES users(id),created_at timestamptz NOT NULL DEFAULT now(),CONSTRAINT uq_confirmation_report_user UNIQUE(report_id,user_id));
CREATE INDEX ix_streets_ward ON streets(ward_id);
CREATE INDEX ix_operator_wards_ward ON operator_wards(ward_id);
CREATE INDEX ix_reports_ward_created ON reports(ward_id,created_at DESC);
CREATE INDEX ix_reports_street ON reports(street_id);
CREATE INDEX ix_reports_reporter ON reports(reporter_id);
CREATE INDEX ix_confirmations_user ON confirmations(user_id);
CREATE INDEX ix_pickups_recorder ON pickups(recorded_by);
CREATE FUNCTION touch_report_updated_at() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN NEW.updated_at=now(); RETURN NEW; END $$;
CREATE TRIGGER reports_updated_at BEFORE UPDATE ON reports FOR EACH ROW EXECUTE FUNCTION touch_report_updated_at();

