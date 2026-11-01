-- Metadata-only renames: preserve every existing confirmation UUID and timestamp.
ALTER TABLE confirmations RENAME TO report_confirmations;
ALTER TABLE report_confirmations RENAME COLUMN user_id TO resident_id;
ALTER TABLE report_confirmations RENAME COLUMN created_at TO confirmed_at;
ALTER TABLE report_confirmations RENAME CONSTRAINT uq_confirmation_report_user TO uq_report_confirmation_resident;
ALTER TABLE report_confirmations RENAME CONSTRAINT confirmations_pkey TO report_confirmations_pkey;
ALTER TABLE report_confirmations RENAME CONSTRAINT confirmations_report_id_fkey TO report_confirmations_report_id_fkey;
ALTER TABLE report_confirmations RENAME CONSTRAINT confirmations_user_id_fkey TO report_confirmations_resident_id_fkey;
ALTER INDEX ix_confirmations_user RENAME TO ix_report_confirmations_resident;
