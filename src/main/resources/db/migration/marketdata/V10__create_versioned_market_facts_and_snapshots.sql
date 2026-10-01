-- V1–V9 evidence remains in its original tables and retains its original keys.
CREATE TABLE marketdata.swap_revisions (
    revision_key VARCHAR(71) PRIMARY KEY,
    chain_id VARCHAR(41) COLLATE "C" NOT NULL,
    transaction_value VARCHAR(512) COLLATE "C" NOT NULL,
    event_locator VARCHAR(256) COLLATE "C" NOT NULL,
    source_kind VARCHAR(32) NOT NULL,
    source_identity VARCHAR(1024) COLLATE "C" NOT NULL,
    provider VARCHAR(64) COLLATE "C" NOT NULL,
    raw_payload_hash VARCHAR(71) NOT NULL,
    derivation_version VARCHAR(128) NOT NULL,
    content_digest VARCHAR(71) NOT NULL,
    availability_status VARCHAR(32) NOT NULL,
    available_at TIMESTAMPTZ(6),
    asset_address VARCHAR(256) COLLATE "C" NOT NULL,
    wallet_address VARCHAR(256) COLLATE "C" NOT NULL,
    side VARCHAR(4) NOT NULL,
    token_quantity NUMERIC(78, 0) NOT NULL,
    native_quantity NUMERIC(78, 0) NOT NULL,
    price_usd NUMERIC(38, 18) NOT NULL,
    liquidity_usd NUMERIC(38, 8) NOT NULL,
    confidence NUMERIC(5, 4) NOT NULL,
    venue VARCHAR(128) COLLATE "C" NOT NULL,
    observed_block_position BIGINT NOT NULL,
    observed_block_hash VARCHAR(256),
    source_event_time TIMESTAMPTZ(6),
    observed_at TIMESTAMPTZ(6) NOT NULL,
    CONSTRAINT swap_revisions_source_kind_check CHECK (source_kind IN ('RAW_CHAIN_EVENT', 'RAW_TRANSACTION')),
    CONSTRAINT swap_revisions_availability_check CHECK (
        (availability_status = 'HISTORICAL_MODEL' AND available_at IS NULL) OR
        (availability_status = 'VERIFIED_REALTIME' AND available_at IS NOT NULL)),
    CONSTRAINT swap_revisions_side_check CHECK (side IN ('BUY', 'SELL')),
    CONSTRAINT swap_revisions_values_check CHECK (token_quantity > 0 AND native_quantity >= 0
        AND price_usd > 0 AND liquidity_usd >= 0 AND confidence BETWEEN 0 AND 1),
    CONSTRAINT swap_revisions_digest_check CHECK (revision_key ~ '^sha256:[0-9a-f]{64}$'
        AND raw_payload_hash ~ '^sha256:[0-9a-f]{64}$' AND content_digest ~ '^sha256:[0-9a-f]{64}$')
);
CREATE INDEX swap_revisions_scope_idx ON marketdata.swap_revisions
    (chain_id, asset_address, observed_at, transaction_value, event_locator, revision_key);

CREATE TABLE marketdata.price_revisions (
    revision_key VARCHAR(71) PRIMARY KEY,
    chain_id VARCHAR(41) COLLATE "C" NOT NULL,
    transaction_value VARCHAR(512) COLLATE "C" NOT NULL,
    event_locator VARCHAR(256) COLLATE "C" NOT NULL,
    source_kind VARCHAR(32) NOT NULL,
    source_identity VARCHAR(1024) COLLATE "C" NOT NULL,
    provider VARCHAR(64) COLLATE "C" NOT NULL,
    raw_payload_hash VARCHAR(71) NOT NULL,
    derivation_version VARCHAR(128) NOT NULL,
    content_digest VARCHAR(71) NOT NULL,
    availability_status VARCHAR(32) NOT NULL,
    available_at TIMESTAMPTZ(6),
    asset_address VARCHAR(256) COLLATE "C" NOT NULL,
    quote_asset_address VARCHAR(256) COLLATE "C" NOT NULL,
    venue VARCHAR(128) COLLATE "C" NOT NULL,
    price NUMERIC(38, 18) NOT NULL,
    trade_notional_quote NUMERIC(38, 8) NOT NULL,
    observed_block_position BIGINT NOT NULL,
    observed_at TIMESTAMPTZ(6) NOT NULL,
    confidence NUMERIC(5, 4) NOT NULL,
    source_event_time TIMESTAMPTZ(6),
    CONSTRAINT price_revisions_source_kind_check CHECK (source_kind IN ('RAW_CHAIN_EVENT', 'RAW_TRANSACTION')),
    CONSTRAINT price_revisions_availability_check CHECK (
        (availability_status = 'HISTORICAL_MODEL' AND available_at IS NULL) OR
        (availability_status = 'VERIFIED_REALTIME' AND available_at IS NOT NULL)),
    CONSTRAINT price_revisions_values_check CHECK (price > 0 AND trade_notional_quote >= 0
        AND confidence BETWEEN 0 AND 1),
    CONSTRAINT price_revisions_digest_check CHECK (revision_key ~ '^sha256:[0-9a-f]{64}$'
        AND raw_payload_hash ~ '^sha256:[0-9a-f]{64}$' AND content_digest ~ '^sha256:[0-9a-f]{64}$')
);
CREATE INDEX price_revisions_scope_idx ON marketdata.price_revisions
    (chain_id, asset_address, observed_at, transaction_value, event_locator, revision_key);

CREATE TABLE marketdata.liquidity_revisions (
    revision_key VARCHAR(71) PRIMARY KEY,
    chain_id VARCHAR(41) COLLATE "C" NOT NULL,
    transaction_value VARCHAR(512) COLLATE "C" NOT NULL,
    event_locator VARCHAR(256) COLLATE "C" NOT NULL,
    source_kind VARCHAR(32) NOT NULL,
    source_identity VARCHAR(1024) COLLATE "C" NOT NULL,
    provider VARCHAR(64) COLLATE "C" NOT NULL,
    raw_payload_hash VARCHAR(71) NOT NULL,
    derivation_version VARCHAR(128) NOT NULL,
    content_digest VARCHAR(71) NOT NULL,
    availability_status VARCHAR(32) NOT NULL,
    available_at TIMESTAMPTZ(6),
    asset_address VARCHAR(256) COLLATE "C" NOT NULL,
    pool_address VARCHAR(256) COLLATE "C" NOT NULL,
    quote_asset_address VARCHAR(256) COLLATE "C" NOT NULL,
    liquidity_usd NUMERIC(38, 8) NOT NULL,
    base_reserve NUMERIC(78, 0) NOT NULL,
    quote_reserve NUMERIC(78, 0) NOT NULL,
    observed_block_position BIGINT NOT NULL,
    observed_at TIMESTAMPTZ(6) NOT NULL,
    confidence NUMERIC(5, 4) NOT NULL,
    source_event_time TIMESTAMPTZ(6),
    CONSTRAINT liquidity_revisions_source_kind_check CHECK (source_kind IN ('RAW_CHAIN_EVENT', 'RAW_TRANSACTION')),
    CONSTRAINT liquidity_revisions_availability_check CHECK (
        (availability_status = 'HISTORICAL_MODEL' AND available_at IS NULL) OR
        (availability_status = 'VERIFIED_REALTIME' AND available_at IS NOT NULL)),
    CONSTRAINT liquidity_revisions_values_check CHECK (liquidity_usd >= 0 AND base_reserve >= 0
        AND quote_reserve >= 0 AND confidence BETWEEN 0 AND 1),
    CONSTRAINT liquidity_revisions_digest_check CHECK (revision_key ~ '^sha256:[0-9a-f]{64}$'
        AND raw_payload_hash ~ '^sha256:[0-9a-f]{64}$' AND content_digest ~ '^sha256:[0-9a-f]{64}$')
);
CREATE INDEX liquidity_revisions_scope_idx ON marketdata.liquidity_revisions
    (chain_id, asset_address, observed_at, transaction_value, event_locator, pool_address, revision_key);

CREATE TABLE marketdata.usd_revisions (
    revision_key VARCHAR(71) PRIMARY KEY,
    chain_id VARCHAR(41) COLLATE "C" NOT NULL,
    transaction_value VARCHAR(512) COLLATE "C" NOT NULL,
    event_locator VARCHAR(256) COLLATE "C" NOT NULL,
    asset_address VARCHAR(256) COLLATE "C" NOT NULL,
    converted_price_revision_key VARCHAR(71) NOT NULL REFERENCES marketdata.price_revisions (revision_key),
    quote_price_revision_key VARCHAR(71) NOT NULL REFERENCES marketdata.price_revisions (revision_key),
    method_version VARCHAR(128) NOT NULL,
    price_usd NUMERIC(38, 18) NOT NULL,
    computed_at TIMESTAMPTZ(6) NOT NULL,
    content_digest VARCHAR(71) NOT NULL,
    availability_status VARCHAR(32) NOT NULL,
    available_at TIMESTAMPTZ(6),
    CONSTRAINT usd_revisions_values_check CHECK (price_usd > 0),
    CONSTRAINT usd_revisions_availability_check CHECK (
        (availability_status = 'HISTORICAL_MODEL' AND available_at IS NULL) OR
        (availability_status = 'VERIFIED_REALTIME' AND available_at IS NOT NULL)),
    CONSTRAINT usd_revisions_digest_check CHECK (revision_key ~ '^sha256:[0-9a-f]{64}$'
        AND content_digest ~ '^sha256:[0-9a-f]{64}$')
);
CREATE INDEX usd_revisions_scope_idx ON marketdata.usd_revisions
    (chain_id, asset_address, computed_at, transaction_value, event_locator, revision_key);

CREATE TABLE marketdata.v2_dataset_snapshots (
    snapshot_id VARCHAR(71) PRIMARY KEY,
    fingerprint VARCHAR(71) NOT NULL UNIQUE,
    canonicalization_version VARCHAR(128) NOT NULL,
    selection_version VARCHAR(128) NOT NULL,
    policy_version VARCHAR(128) NOT NULL,
    availability_status VARCHAR(32) NOT NULL,
    knowledge_cutoff TIMESTAMPTZ(6) NOT NULL,
    scope_manifest JSONB NOT NULL,
    included_count INTEGER NOT NULL,
    excluded_count INTEGER NOT NULL,
    covered_key_count INTEGER NOT NULL,
    CONSTRAINT v2_dataset_snapshots_counts_check CHECK (included_count >= 0 AND excluded_count >= 0
        AND covered_key_count = included_count + excluded_count AND covered_key_count <= 10000),
    CONSTRAINT v2_dataset_snapshots_availability_check CHECK (availability_status IN ('HISTORICAL_MODEL', 'VERIFIED_REALTIME')),
    CONSTRAINT v2_dataset_snapshots_digest_check CHECK (snapshot_id ~ '^sha256:[0-9a-f]{64}$'
        AND fingerprint ~ '^sha256:[0-9a-f]{64}$')
);
CREATE TABLE marketdata.v2_dataset_snapshot_members (
    snapshot_id VARCHAR(71) NOT NULL REFERENCES marketdata.v2_dataset_snapshots (snapshot_id),
    member_ordinal INTEGER NOT NULL,
    fact_kind VARCHAR(16) NOT NULL,
    chain_id VARCHAR(41) COLLATE "C" NOT NULL,
    transaction_value VARCHAR(512) COLLATE "C" NOT NULL,
    event_locator VARCHAR(256) COLLATE "C" NOT NULL,
    asset_address VARCHAR(256) COLLATE "C" NOT NULL,
    scope_dimension VARCHAR(256) COLLATE "C" NOT NULL,
    revision_key VARCHAR(71) NOT NULL,
    content_digest VARCHAR(71) NOT NULL,
    swap_revision_key VARCHAR(71) REFERENCES marketdata.swap_revisions (revision_key),
    price_revision_key VARCHAR(71) REFERENCES marketdata.price_revisions (revision_key),
    liquidity_revision_key VARCHAR(71) REFERENCES marketdata.liquidity_revisions (revision_key),
    usd_revision_key VARCHAR(71) REFERENCES marketdata.usd_revisions (revision_key),
    CONSTRAINT v2_dataset_snapshot_members_pk PRIMARY KEY (snapshot_id, member_ordinal),
    CONSTRAINT v2_dataset_snapshot_members_unique UNIQUE
        (snapshot_id, fact_kind, chain_id, transaction_value, event_locator, asset_address, scope_dimension),
    CONSTRAINT v2_dataset_snapshot_members_typed_check CHECK (
        (fact_kind = 'SWAP' AND revision_key = swap_revision_key AND price_revision_key IS NULL AND liquidity_revision_key IS NULL AND usd_revision_key IS NULL) OR
        (fact_kind = 'PRICE' AND revision_key = price_revision_key AND swap_revision_key IS NULL AND liquidity_revision_key IS NULL AND usd_revision_key IS NULL) OR
        (fact_kind = 'LIQUIDITY' AND revision_key = liquidity_revision_key AND swap_revision_key IS NULL AND price_revision_key IS NULL AND usd_revision_key IS NULL) OR
        (fact_kind = 'USD' AND revision_key = usd_revision_key AND swap_revision_key IS NULL AND price_revision_key IS NULL AND liquidity_revision_key IS NULL))
);
CREATE TABLE marketdata.v2_dataset_snapshot_exclusions (
    snapshot_id VARCHAR(71) NOT NULL REFERENCES marketdata.v2_dataset_snapshots (snapshot_id),
    exclusion_ordinal INTEGER NOT NULL,
    fact_kind VARCHAR(16) NOT NULL,
    chain_id VARCHAR(41) COLLATE "C" NOT NULL,
    transaction_value VARCHAR(512) COLLATE "C" NOT NULL,
    event_locator VARCHAR(256) COLLATE "C" NOT NULL,
    asset_address VARCHAR(256) COLLATE "C" NOT NULL,
    scope_dimension VARCHAR(256) COLLATE "C" NOT NULL,
    reason VARCHAR(128) NOT NULL,
    evidence_fingerprint VARCHAR(71) NOT NULL,
    CONSTRAINT v2_dataset_snapshot_exclusions_pk PRIMARY KEY (snapshot_id, exclusion_ordinal),
    CONSTRAINT v2_dataset_snapshot_exclusions_unique UNIQUE
        (snapshot_id, fact_kind, chain_id, transaction_value, event_locator, asset_address, scope_dimension),
    CONSTRAINT v2_dataset_snapshot_exclusions_kind_check CHECK (fact_kind IN ('SWAP','PRICE','LIQUIDITY','USD')),
    CONSTRAINT v2_dataset_snapshot_exclusions_evidence_check CHECK (evidence_fingerprint ~ '^sha256:[0-9a-f]{64}$')
);
