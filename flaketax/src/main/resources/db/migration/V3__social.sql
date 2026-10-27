ALTER TABLE pacts ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE pacts DROP CONSTRAINT IF EXISTS pacts_status_check;
ALTER TABLE pacts ADD CONSTRAINT pacts_status_check CHECK (status IN ('OPEN', 'SETTLED', 'CANCELLED'));
ALTER TABLE members ADD COLUMN photo_key VARCHAR(80);
ALTER TABLE members ADD COLUMN photo_type VARCHAR(30);

CREATE TABLE friends (
                         id BIGSERIAL PRIMARY KEY,
                         a_id BIGINT NOT NULL REFERENCES users(id),
                         b_id BIGINT NOT NULL REFERENCES users(id),
                         asked_by BIGINT NOT NULL REFERENCES users(id),
                         state VARCHAR(12) NOT NULL CHECK (state IN ('PENDING', 'ACCEPTED')),
                         CHECK (a_id < b_id),
                         CHECK (asked_by = a_id OR asked_by = b_id),
                         UNIQUE (a_id, b_id)
);
CREATE INDEX idx_friends_b ON friends(b_id);
CREATE INDEX idx_pacts_lobby ON pacts(status, archived, deadline);