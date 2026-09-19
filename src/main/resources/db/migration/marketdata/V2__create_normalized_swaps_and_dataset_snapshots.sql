CREATE TABLE marketdata.normalized_swaps (
    chain_id VARCHAR(41) COLLATE "C" NOT NULL,
    transaction_value VARCHAR(512) COLLATE "C" NOT NULL,
    event_locator VARCHAR(256) COLLATE "C" NOT NULL,
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
    provider VARCHAR(64) COLLATE "C" NOT NULL,
    raw_payload_hash VARCHAR(71) NOT NULL,
    transformation_version VARCHAR(128) NOT NULL,
    CONSTRAINT normalized_swaps_pk PRIMARY KEY (chain_id, transaction_value, event_locator),
    CONSTRAINT normalized_swaps_raw_fk FOREIGN KEY (chain_id, transaction_value, event_locator, provider)
        REFERENCES marketdata.raw_chain_events (chain_id, transaction_value, event_locator, provider),
    CONSTRAINT normalized_swaps_side_check CHECK (side IN ('BUY', 'SELL')),
    CONSTRAINT normalized_swaps_token_quantity_check CHECK (token_quantity > 0),
    CONSTRAINT normalized_swaps_native_quantity_check CHECK (native_quantity >= 0),
    CONSTRAINT normalized_swaps_price_usd_check CHECK (price_usd > 0),
    CONSTRAINT normalized_swaps_liquidity_usd_check CHECK (liquidity_usd >= 0),
    CONSTRAINT normalized_swaps_confidence_check CHECK (confidence BETWEEN 0.0000 AND 1.0000),
    CONSTRAINT normalized_swaps_block_position_check CHECK (observed_block_position >= 0),
    CONSTRAINT normalized_swaps_payload_hash_check CHECK (raw_payload_hash ~ '^sha256:[0-9a-f]{64}$'),
    CONSTRAINT normalized_swaps_transformation_version_check CHECK (
        transformation_version <> '' AND transformation_version = btrim(transformation_version)),
    CONSTRAINT normalized_swaps_observation_identity_check CHECK (
        asset_address <> '' AND wallet_address <> '' AND venue <> '')
);

CREATE INDEX normalized_swaps_point_in_time_idx
    ON marketdata.normalized_swaps (chain_id, asset_address, observed_at, transaction_value, event_locator);

CREATE TABLE marketdata.dataset_snapshots (
    snapshot_id VARCHAR(71) PRIMARY KEY,
    fingerprint VARCHAR(71) NOT NULL UNIQUE,
    canonicalization_version VARCHAR(128) NOT NULL,
    cutoff TIMESTAMPTZ(6) NOT NULL,
    CONSTRAINT dataset_snapshots_id_check CHECK (snapshot_id ~ '^sha256:[0-9a-f]{64}$'),
    CONSTRAINT dataset_snapshots_fingerprint_check CHECK (fingerprint ~ '^sha256:[0-9a-f]{64}$'),
    CONSTRAINT dataset_snapshots_canonicalization_version_check CHECK (
        canonicalization_version <> '' AND canonicalization_version = btrim(canonicalization_version))
);

CREATE TABLE marketdata.dataset_snapshot_members (
    snapshot_id VARCHAR(71) NOT NULL,
    member_ordinal INTEGER NOT NULL,
    chain_id VARCHAR(41) COLLATE "C" NOT NULL,
    transaction_value VARCHAR(512) COLLATE "C" NOT NULL,
    event_locator VARCHAR(256) COLLATE "C" NOT NULL,
    CONSTRAINT dataset_snapshot_members_pk PRIMARY KEY (snapshot_id, member_ordinal),
    CONSTRAINT dataset_snapshot_members_identity_unique
        UNIQUE (snapshot_id, chain_id, transaction_value, event_locator),
    CONSTRAINT dataset_snapshot_members_snapshot_fk FOREIGN KEY (snapshot_id)
        REFERENCES marketdata.dataset_snapshots (snapshot_id),
    CONSTRAINT dataset_snapshot_members_swap_fk FOREIGN KEY (chain_id, transaction_value, event_locator)
        REFERENCES marketdata.normalized_swaps (chain_id, transaction_value, event_locator),
    CONSTRAINT dataset_snapshot_members_ordinal_check CHECK (member_ordinal >= 0)
);
