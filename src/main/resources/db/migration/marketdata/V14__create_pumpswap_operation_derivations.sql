CREATE TABLE marketdata.pumpswap_transaction_derivations (
    revision_key VARCHAR(71) COLLATE "C" PRIMARY KEY CHECK (revision_key ~ '^sha256:[0-9a-f]{64}$'),
    chain_id VARCHAR(41) COLLATE "C" NOT NULL,
    transaction_value VARCHAR(512) COLLATE "C" NOT NULL,
    provider VARCHAR(64) COLLATE "C" NOT NULL,
    raw_digest VARCHAR(71) COLLATE "C" NOT NULL CHECK (raw_digest ~ '^sha256:[0-9a-f]{64}$'),
    content_digest VARCHAR(71) COLLATE "C" NOT NULL CHECK (content_digest ~ '^sha256:[0-9a-f]{64}$'),
    content TEXT NOT NULL CHECK (octet_length(content) BETWEEN 1 AND 64000000),
    operation_count INTEGER NOT NULL CHECK (operation_count BETWEEN 0 AND 10000),
    persisted_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (revision_key, chain_id, transaction_value),
    FOREIGN KEY (chain_id, transaction_value, provider)
        REFERENCES marketdata.raw_transactions (chain_id, transaction_value, provider)
);
CREATE TABLE marketdata.pumpswap_operation_derivations (
    revision_key VARCHAR(71) COLLATE "C" PRIMARY KEY CHECK (revision_key ~ '^sha256:[0-9a-f]{64}$'),
    canonical_key VARCHAR(71) COLLATE "C" NOT NULL CHECK (canonical_key ~ '^sha256:[0-9a-f]{64}$'),
    transaction_revision_key VARCHAR(71) COLLATE "C" NOT NULL,
    chain_id VARCHAR(41) COLLATE "C" NOT NULL,
    transaction_value VARCHAR(512) COLLATE "C" NOT NULL,
    outer_index INTEGER NOT NULL CHECK (outer_index >= 0),
    inner_path INTEGER[] NOT NULL CHECK (array_position(inner_path, NULL) IS NULL AND 0 <= ALL(inner_path)),
    leg_ordinal INTEGER NOT NULL CHECK (leg_ordinal >= 0),
    content_digest VARCHAR(71) COLLATE "C" NOT NULL CHECK (content_digest ~ '^sha256:[0-9a-f]{64}$'),
    content TEXT NOT NULL CHECK (octet_length(content) BETWEEN 1 AND 64000000),
    persisted_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (transaction_revision_key, canonical_key),
    FOREIGN KEY (transaction_revision_key, chain_id, transaction_value)
        REFERENCES marketdata.pumpswap_transaction_derivations (revision_key, chain_id, transaction_value)
);
-- Exact operation set reads for batch-member transaction revisions; no latest-version selection.
CREATE INDEX pumpswap_operation_parent_idx ON marketdata.pumpswap_operation_derivations (transaction_revision_key);
CREATE TABLE marketdata.pumpswap_decode_batches (
    batch_key VARCHAR(71) COLLATE "C" PRIMARY KEY CHECK (batch_key ~ '^sha256:[0-9a-f]{64}$'),
    content_digest VARCHAR(71) COLLATE "C" NOT NULL CHECK (content_digest ~ '^sha256:[0-9a-f]{64}$'),
    content TEXT NOT NULL CHECK (octet_length(content) BETWEEN 1 AND 64000000),
    transaction_count INTEGER NOT NULL CHECK (transaction_count BETWEEN 0 AND 128),
    operation_count INTEGER NOT NULL CHECK (operation_count BETWEEN 0 AND 10000),
    persisted_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE marketdata.pumpswap_decode_batch_members (
    batch_key VARCHAR(71) COLLATE "C" NOT NULL REFERENCES marketdata.pumpswap_decode_batches (batch_key),
    ordinal INTEGER NOT NULL CHECK (ordinal BETWEEN 0 AND 127),
    transaction_revision_key VARCHAR(71) COLLATE "C" NOT NULL REFERENCES marketdata.pumpswap_transaction_derivations (revision_key),
    PRIMARY KEY (batch_key, ordinal),
    UNIQUE (batch_key, transaction_revision_key)
);
