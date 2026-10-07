CREATE TABLE marketdata.capture_periods (
    account_period TEXT COLLATE "C" PRIMARY KEY,
    authority_json TEXT NOT NULL,
    ceiling_hundredths BIGINT NOT NULL CHECK (ceiling_hundredths > 0),
    reserved_hundredths BIGINT NOT NULL CHECK (reserved_hundredths >= 0),
    period_end TIMESTAMPTZ(6) NOT NULL,
    known_overage_hundredths BIGINT NOT NULL DEFAULT 0 CHECK (known_overage_hundredths >= 0),
    CHECK (reserved_hundredths <= ceiling_hundredths)
);

CREATE TABLE marketdata.capture_sessions (
    session_id UUID PRIMARY KEY,
    configuration_json TEXT NOT NULL,
    configuration_digest TEXT NOT NULL CHECK (configuration_digest ~ '^sha256:[0-9a-f]{64}$'),
    account_period TEXT COLLATE "C" NOT NULL REFERENCES marketdata.capture_periods,
    owner_authorization TEXT COLLATE "C" NOT NULL,
    holder TEXT COLLATE "C" NOT NULL,
    locked_until TIMESTAMPTZ(6) NOT NULL,
    started_at TIMESTAMPTZ(6) NOT NULL,
    ends_at TIMESTAMPTZ(6) NOT NULL,
    state TEXT NOT NULL,
    reason TEXT NOT NULL DEFAULT '',
    requests BIGINT NOT NULL DEFAULT 0 CHECK (requests >= 0),
    received_bytes BIGINT NOT NULL DEFAULT 0 CHECK (received_bytes >= 0),
    subscription_bytes BIGINT NOT NULL DEFAULT 0 CHECK (subscription_bytes >= 0),
    retained_bytes BIGINT NOT NULL DEFAULT 0 CHECK (retained_bytes >= 0),
    rpc_hundredths BIGINT NOT NULL DEFAULT 0 CHECK (rpc_hundredths >= 0),
    reserved_hundredths BIGINT NOT NULL CHECK (reserved_hundredths > 0),
    UNIQUE (account_period, owner_authorization)
);

CREATE TABLE marketdata.capture_dispositions (
    session_id UUID NOT NULL REFERENCES marketdata.capture_sessions,
    pool TEXT COLLATE "C" NOT NULL,
    signature TEXT COLLATE "C" NOT NULL,
    slot BIGINT NOT NULL CHECK (slot >= 0),
    status TEXT NOT NULL,
    raw_digest TEXT,
    notification TEXT NOT NULL,
    response TEXT NOT NULL DEFAULT '',
    received_at TIMESTAMPTZ(6) NOT NULL,
    admitted_at TIMESTAMPTZ(6),
    duplicates BIGINT NOT NULL DEFAULT 0 CHECK (duplicates >= 0),
    conflicts BIGINT NOT NULL DEFAULT 0 CHECK (conflicts >= 0),
    PRIMARY KEY (session_id, pool, signature)
);
CREATE INDEX capture_pending_idx ON marketdata.capture_dispositions (session_id, status, slot, pool, signature);

CREATE TABLE marketdata.capture_checkpoints (
    session_id UUID NOT NULL,
    pool TEXT COLLATE "C" NOT NULL,
    signature TEXT COLLATE "C" NOT NULL,
    PRIMARY KEY (session_id, pool),
    FOREIGN KEY (session_id, pool, signature) REFERENCES marketdata.capture_dispositions
);

CREATE TABLE marketdata.raw_account_snapshots (
    chain_id VARCHAR(41) COLLATE "C" NOT NULL CHECK (chain_id = 'solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp'),
    provider VARCHAR(64) COLLATE "C" NOT NULL,
    account TEXT COLLATE "C" NOT NULL,
    slot BIGINT NOT NULL CHECK (slot >= 0),
    digest TEXT COLLATE "C" NOT NULL CHECK (digest ~ '^sha256:[0-9a-f]{64}$'),
    encoding TEXT NOT NULL,
    payload TEXT NOT NULL,
    envelope TEXT NOT NULL,
    received_at TIMESTAMPTZ(6) NOT NULL,
    admitted_at TIMESTAMPTZ(6) NOT NULL,
    ingested_at TIMESTAMPTZ(6) NOT NULL,
    PRIMARY KEY (chain_id, provider, account, slot, digest),
    CHECK (received_at <= admitted_at AND admitted_at <= ingested_at)
);

CREATE TABLE marketdata.capture_state_members (
    session_id UUID NOT NULL REFERENCES marketdata.capture_sessions,
    chain_id VARCHAR(41) COLLATE "C" NOT NULL,
    provider VARCHAR(64) COLLATE "C" NOT NULL,
    account TEXT COLLATE "C" NOT NULL,
    slot BIGINT NOT NULL,
    digest TEXT COLLATE "C" NOT NULL,
    duplicates BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (session_id, account, slot, digest),
    FOREIGN KEY (chain_id, provider, account, slot, digest) REFERENCES marketdata.raw_account_snapshots
);

CREATE TABLE marketdata.capture_gaps (
    gap_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES marketdata.capture_sessions,
    pool TEXT COLLATE "C" NOT NULL,
    kind TEXT NOT NULL,
    reason TEXT NOT NULL,
    from_slot BIGINT,
    to_slot BIGINT,
    opened_at TIMESTAMPTZ(6) NOT NULL,
    closed_at TIMESTAMPTZ(6),
    boundary_signature TEXT COLLATE "C",
    status TEXT NOT NULL
);
CREATE INDEX capture_gap_status_idx ON marketdata.capture_gaps (session_id, status, pool, gap_id);

CREATE TABLE marketdata.capture_envelopes (
    session_id UUID NOT NULL REFERENCES marketdata.capture_sessions,
    kind TEXT COLLATE "C" NOT NULL,
    digest TEXT COLLATE "C" NOT NULL CHECK (digest ~ '^sha256:[0-9a-f]{64}$'),
    payload TEXT NOT NULL,
    received_at TIMESTAMPTZ(6) NOT NULL,
    PRIMARY KEY (session_id, kind, digest)
);
