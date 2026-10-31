-- Focused read indexes only. Preserve all existing records, constraints and UUIDs.
CREATE INDEX ix_reports_ward_type_created ON reports(ward_id,report_type,created_at DESC,id);
CREATE INDEX ix_reports_missed_street_date ON reports(street_id,collection_date)
 WHERE report_type='MISSED_PICKUP';
CREATE INDEX ix_schedule_date_street ON collection_schedules(collection_date,street_id) INCLUDE(operator_id);
CREATE INDEX ix_schedule_operator_date_street ON collection_schedules(operator_id,collection_date,street_id);
CREATE INDEX ix_confirmation_report_evidence ON report_confirmations(report_id,confirmed_at DESC) INCLUDE(resident_id);
