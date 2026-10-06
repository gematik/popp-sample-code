-- Liquibase changeset to create table, indexes, and insert 10 test records

-- changeset poppserver:1
CREATE TABLE egk_entries (
                             id SERIAL PRIMARY KEY,
                             cvc_hash bytea NOT NULL,
                             aut_hash bytea NOT NULL,
                             state VARCHAR(8) NOT NULL CHECK (state IN ('imported', 'ad hoc', 'blocked')),
                             not_after TIMESTAMP NOT NULL
);

-- ChangeSet for creating the index for fast lookup
-- changeset poppserver:2
CREATE INDEX idx_egk_entries_cvc_hash ON egk_entries (cvc_hash);

-- ChangeSet for creating the index for fast lookup
-- changeset poppserver:3
CREATE INDEX idx_egk_entries_aut_hash ON egk_entries (aut_hash);

-- ChangeSet for inserting 10 test records
-- changeset poppserver:4
INSERT INTO egk_entries (cvc_hash, aut_hash, state, not_after) VALUES
                                                                  (decode('637668617368303031', 'hex'), decode('61757468617368303031', 'hex'), 'imported', '2025-12-31 23:59:59'),
                                                                  (decode('637668617368303032', 'hex'), decode('61757468617368303032', 'hex'), 'ad hoc',   '2025-06-30 23:59:59'),
                                                                  (decode('637668617368303033', 'hex'), decode('61757468617368303033', 'hex'), 'blocked',  '2024-12-01 00:00:00'),
                                                                  (decode('637668617368303034', 'hex'), decode('61757468617368303034', 'hex'), 'imported', '2025-11-15 15:45:00'),
                                                                  (decode('637668617368303035', 'hex'), decode('61757468617368303035', 'hex'), 'ad hoc',   '2025-01-01 00:00:00'),
                                                                  (decode('637668617368303036', 'hex'), decode('61757468617368303036', 'hex'), 'blocked',  '2025-02-20 14:30:00'),
                                                                  (decode('637668617368303037', 'hex'), decode('61757468617368303037', 'hex'), 'imported', '2025-08-15 17:00:00'),
                                                                  (decode('637668617368303038', 'hex'), decode('61757468617368303038', 'hex'), 'ad hoc',   '2024-07-07 12:00:00'),
                                                                  (decode('637668617368303039', 'hex'), decode('61757468617368303039', 'hex'), 'blocked',  '2025-03-25 09:30:00'),
                                                                  (decode('637668617368303130', 'hex'), decode('61757468617368303130', 'hex'), 'imported', '2025-04-10 10:15:00');

-- ChangeSet for creating the table for storing the CVC import reports
-- changeSet poppserver:5

CREATE TABLE import_report_entries (
                                       id SERIAL PRIMARY KEY,
                                       session_id VARCHAR(255) NOT NULL,
                                       start_time TIMESTAMP NOT NULL,
                                       end_time TIMESTAMP,
                                       imported_count BIGINT NOT NULL DEFAULT 0,
                                       blocked_count BIGINT NOT NULL DEFAULT 0,
                                       skipped_count BIGINT NOT NULL DEFAULT 0,
                                       total_processed_count BIGINT NOT NULL DEFAULT 0
);

-- ChangeSet for creating the index for fast lookup on session_id
-- changeSet poppserver:6
CREATE INDEX idx_import_report_entries_session_id ON import_report_entries (session_id);

-- ChangeSet for inserting a test record into import_report_entries
-- changeSet poppserver:7
INSERT INTO import_report_entries (session_id, start_time, end_time, imported_count, blocked_count, skipped_count, total_processed_count) VALUES
                                                                                                  ('test-session-001',
                                                                                                   '2024-01-01 10:00:00',
                                                                                                   '2024-01-01 11:00:00',
                                                                                                   5,
                                                                                                   2,
                                                                                                   3,
                                                                                                   10);

-- changeSet poppserver:8
CREATE TABLE popp_message_records (
                                     id UUID PRIMARY KEY,
                                     status VARCHAR(8) NOT NULL DEFAULT 'pending',
                                     client_id VARCHAR(255) NOT NULL,
                                     created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                                     expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
                                     token_delivered_at TIMESTAMP WITH TIME ZONE,
                                     push_sent_at TIMESTAMP WITH TIME ZONE,
                                     CONSTRAINT chk_popp_message_status
                                         CHECK (status IN ('success', 'pending', 'canceled')),
                                     CONSTRAINT chk_popp_message_expiry
                                         CHECK (expires_at = created_at + INTERVAL '72 hours')
);

-- changeSet poppserver:9
CREATE TABLE popp_token_generation_records (
                                              message_id UUID PRIMARY KEY,
                                              patient_id VARCHAR(10) NOT NULL,
                                              insurer_id VARCHAR(9) NOT NULL,
                                              actor_id VARCHAR(255) NOT NULL,
                                              workplace_id VARCHAR(64),
                                              proof_method VARCHAR(8) NOT NULL DEFAULT 'healthid',
                                              timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
                                              CONSTRAINT fk_popp_token_generation_message
                                                  FOREIGN KEY (message_id)
                                                      REFERENCES popp_message_records (id),
                                              CONSTRAINT chk_popp_token_generation_patient_id
                                                  CHECK (patient_id ~ '^[A-Z][0-9]{9}$'),
                                              CONSTRAINT chk_popp_token_generation_insurer_id
                                                  CHECK (insurer_id ~ '^[0-9]{9}$'),
                                              CONSTRAINT chk_popp_token_generation_proof_method
                                                  CHECK (proof_method = 'healthid')
);

-- changeSet poppserver:10
CREATE INDEX idx_popp_message_records_expires_at ON popp_message_records (expires_at);