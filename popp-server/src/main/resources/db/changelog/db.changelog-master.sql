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
