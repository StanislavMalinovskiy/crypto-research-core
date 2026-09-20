CREATE TABLE marketdata.usd_conversion_facts (
    chain_id VARCHAR(41) COLLATE "C" NOT NULL,
    transaction_value VARCHAR(512) COLLATE "C" NOT NULL,
    event_locator VARCHAR(256) COLLATE "C" NOT NULL,
    asset_address VARCHAR(256) COLLATE "C" NOT NULL,
    usd_quote_transaction_value VARCHAR(512) COLLATE "C" NOT NULL,
    usd_quote_event_locator VARCHAR(256) COLLATE "C" NOT NULL,
    price_usd NUMERIC(38, 18) NOT NULL,
    method_version VARCHAR(128) COLLATE "C" NOT NULL,
    computed_at TIMESTAMPTZ(6) NOT NULL,
    CONSTRAINT usd_conversion_facts_pk
        PRIMARY KEY (chain_id, transaction_value, event_locator),
    CONSTRAINT usd_conversion_facts_chain_id_caip2_check
        CHECK (chain_id ~ '^[-a-z0-9]{3,8}:[-_a-zA-Z0-9]{1,32}$'),
    CONSTRAINT usd_conversion_facts_transaction_value_format_check
        CHECK (transaction_value <> '' AND transaction_value = btrim(transaction_value)
            AND transaction_value !~ '[[:cntrl:]]'),
    CONSTRAINT usd_conversion_facts_event_locator_format_check
        CHECK (event_locator <> '' AND event_locator = btrim(event_locator)
            AND event_locator !~ '[[:cntrl:]]'),
    CONSTRAINT usd_conversion_facts_usd_quote_transaction_format_check
        CHECK (usd_quote_transaction_value <> '' AND usd_quote_transaction_value = btrim(usd_quote_transaction_value)
            AND usd_quote_transaction_value !~ '[[:cntrl:]]'),
    CONSTRAINT usd_conversion_facts_usd_quote_locator_format_check
        CHECK (usd_quote_event_locator <> '' AND usd_quote_event_locator = btrim(usd_quote_event_locator)
            AND usd_quote_event_locator !~ '[[:cntrl:]]'),
    CONSTRAINT usd_conversion_facts_method_version_format_check
        CHECK (method_version <> '' AND method_version = btrim(method_version)
            AND method_version !~ '[[:cntrl:]]'),
    CONSTRAINT usd_conversion_facts_transaction_value_utf8_length_check
        CHECK (octet_length(transaction_value) <= 512),
    CONSTRAINT usd_conversion_facts_event_locator_utf8_length_check
        CHECK (octet_length(event_locator) <= 256),
    CONSTRAINT usd_conversion_facts_asset_utf8_length_check
        CHECK (octet_length(asset_address) <= 256),
    CONSTRAINT usd_conversion_facts_usd_quote_transaction_utf8_length_check
        CHECK (octet_length(usd_quote_transaction_value) <= 512),
    CONSTRAINT usd_conversion_facts_usd_quote_locator_utf8_length_check
        CHECK (octet_length(usd_quote_event_locator) <= 256),
    CONSTRAINT usd_conversion_facts_method_version_utf8_length_check
        CHECK (octet_length(method_version) <= 128),
    CONSTRAINT usd_conversion_facts_price_positive_check
        CHECK (price_usd > 0)
);

CREATE TABLE marketdata.universe_snapshots (
    snapshot_id VARCHAR(71) COLLATE "C" NOT NULL,
    fingerprint VARCHAR(71) COLLATE "C" NOT NULL,
    rule_version VARCHAR(128) COLLATE "C" NOT NULL,
    cutoff TIMESTAMPTZ(6) NOT NULL,
    CONSTRAINT universe_snapshots_pk PRIMARY KEY (snapshot_id),
    CONSTRAINT universe_snapshots_fingerprint_unique UNIQUE (fingerprint),
    CONSTRAINT universe_snapshots_id_check
        CHECK (snapshot_id ~ '^sha256:[0-9a-f]{64}$'),
    CONSTRAINT universe_snapshots_fingerprint_check
        CHECK (fingerprint ~ '^sha256:[0-9a-f]{64}$'),
    CONSTRAINT universe_snapshots_rule_version_format_check
        CHECK (rule_version <> '' AND rule_version = btrim(rule_version)
            AND rule_version !~ '[[:cntrl:]]')
);

CREATE TABLE marketdata.universe_members (
    snapshot_id VARCHAR(71) COLLATE "C" NOT NULL,
    member_ordinal BIGINT NOT NULL,
    chain_id VARCHAR(41) COLLATE "C" NOT NULL,
    asset_address VARCHAR(256) COLLATE "C" NOT NULL,
    discovery_source VARCHAR(128) COLLATE "C" NOT NULL,
    eligibility_rule_version VARCHAR(128) COLLATE "C" NOT NULL,
    inclusion_time TIMESTAMPTZ(6) NOT NULL,
    exclusion_time TIMESTAMPTZ(6),
    exclusion_reason VARCHAR(256),
    CONSTRAINT universe_members_pk PRIMARY KEY (snapshot_id, chain_id, asset_address),
    CONSTRAINT universe_members_snapshot_fk
        FOREIGN KEY (snapshot_id) REFERENCES marketdata.universe_snapshots (snapshot_id),
    CONSTRAINT universe_members_ordinal_unique UNIQUE (snapshot_id, member_ordinal),
    CONSTRAINT universe_members_chain_id_caip2_check
        CHECK (chain_id ~ '^[-a-z0-9]{3,8}:[-_a-zA-Z0-9]{1,32}$'),
    CONSTRAINT universe_members_asset_format_check
        CHECK (asset_address <> '' AND asset_address = btrim(asset_address)
            AND asset_address !~ '[[:cntrl:]]'),
    CONSTRAINT universe_members_discovery_source_format_check
        CHECK (discovery_source <> '' AND discovery_source = btrim(discovery_source)
            AND discovery_source !~ '[[:cntrl:]]'),
    CONSTRAINT universe_members_eligibility_rule_format_check
        CHECK (eligibility_rule_version <> '' AND eligibility_rule_version = btrim(eligibility_rule_version)
            AND eligibility_rule_version !~ '[[:cntrl:]]'),
    CONSTRAINT universe_members_asset_utf8_length_check
        CHECK (octet_length(asset_address) <= 256),
    CONSTRAINT universe_members_discovery_source_utf8_length_check
        CHECK (octet_length(discovery_source) <= 128),
    CONSTRAINT universe_members_eligibility_rule_utf8_length_check
        CHECK (octet_length(eligibility_rule_version) <= 128),
    CONSTRAINT universe_members_exclusion_reason_utf8_length_check
        CHECK (exclusion_reason IS NULL OR octet_length(exclusion_reason) <= 256),
    CONSTRAINT universe_members_exclusion_order_check
        CHECK (exclusion_time IS NULL OR exclusion_time > inclusion_time),
    CONSTRAINT universe_members_exclusion_reason_shape_check
        CHECK ((exclusion_time IS NULL AND exclusion_reason IS NULL)
            OR (exclusion_time IS NOT NULL AND exclusion_reason IS NOT NULL
                AND exclusion_reason <> '' AND btrim(exclusion_reason) = exclusion_reason))
);
