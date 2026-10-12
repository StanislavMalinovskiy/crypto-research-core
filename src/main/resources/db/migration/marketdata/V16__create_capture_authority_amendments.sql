CREATE TABLE marketdata.capture_authority_amendments (
    account_period TEXT COLLATE "C" PRIMARY KEY REFERENCES marketdata.capture_periods,
    version TEXT NOT NULL CHECK (version = 'capture-authority-amendment-v1'),
    provider TEXT COLLATE "C" NOT NULL CHECK (provider = 'alchemy'),
    predecessor_authority_json TEXT NOT NULL CHECK (octet_length(predecessor_authority_json) <= 1000000),
    predecessor_ceiling_hundredths BIGINT NOT NULL CHECK (predecessor_ceiling_hundredths > 0),
    amended_authority_json TEXT NOT NULL CHECK (octet_length(amended_authority_json) <= 1000000),
    effective_ceiling_hundredths BIGINT NOT NULL CHECK (effective_ceiling_hundredths >= predecessor_ceiling_hundredths),
    period_end TIMESTAMPTZ(6) NOT NULL,
    owner_decision TEXT COLLATE "C" NOT NULL CHECK (octet_length(owner_decision) BETWEEN 1 AND 16384),
    request_json TEXT NOT NULL CHECK (octet_length(request_json) <= 1000000),
    amendment_digest TEXT COLLATE "C" NOT NULL CHECK (amendment_digest ~ '^sha256:[0-9a-f]{64}$'),
    applied_at TIMESTAMPTZ(6) NOT NULL
);
