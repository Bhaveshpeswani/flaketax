
CREATE TABLE users (
                       id BIGSERIAL PRIMARY KEY,
                       handle VARCHAR(20) NOT NULL UNIQUE,
                       pass_hash VARCHAR(100) NOT NULL,
                       balance BIGINT NOT NULL CHECK (balance >= 0)
);

CREATE TABLE pacts (
                       id BIGSERIAL PRIMARY KEY,
                       host_id BIGINT NOT NULL REFERENCES users(id),
                       title VARCHAR(120) NOT NULL,
                       stake BIGINT NOT NULL CHECK (stake BETWEEN 1 AND 1000),
                       pot BIGINT NOT NULL DEFAULT 0 CHECK (pot >= 0),
                       status VARCHAR(20) NOT NULL
                           CHECK (status IN ('OPEN', 'SETTLED')),
                       deadline TIMESTAMPTZ NOT NULL,
                       review_end TIMESTAMPTZ NOT NULL,
                       CHECK (review_end > deadline)
);

CREATE TABLE members (
                         id BIGSERIAL PRIMARY KEY,
                         pact_id BIGINT NOT NULL REFERENCES pacts(id),
                         user_id BIGINT NOT NULL REFERENCES users(id),
                         status VARCHAR(20) NOT NULL
                             CHECK (status IN (
                                               'JOINED',
                                               'SUBMITTED',
                                               'APPROVED',
                                               'REJECTED'
                                 )),
                         proof VARCHAR(500),
                         sent_at TIMESTAMPTZ,
                         UNIQUE (pact_id, user_id)
);

CREATE TABLE entries (
                         id BIGSERIAL PRIMARY KEY,
                         user_id BIGINT REFERENCES users(id),
                         pact_id BIGINT REFERENCES pacts(id),
                         kind VARCHAR(20) NOT NULL
                             CHECK (kind IN (
                                             'START',
                                             'HOLD',
                                             'PAYOUT',
                                             'REFUND'
                                 )),
                         amount BIGINT NOT NULL CHECK (amount <> 0),
                         made_at TIMESTAMPTZ NOT NULL,
                         CHECK (user_id IS NOT NULL OR pact_id IS NOT NULL)
);

CREATE INDEX idx_pacts_due
    ON pacts(review_end)
    WHERE status = 'OPEN';

CREATE INDEX idx_members_pact
    ON members(pact_id, user_id);

CREATE INDEX idx_entries_user
    ON entries(user_id, id DESC);
