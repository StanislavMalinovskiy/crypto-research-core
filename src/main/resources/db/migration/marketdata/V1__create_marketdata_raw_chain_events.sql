CREATE SCHEMA marketdata;

CREATE TABLE marketdata.raw_chain_events (
    chain_id VARCHAR(41) COLLATE "C" NOT NULL,
    transaction_value VARCHAR(512) COLLATE "C" NOT NULL,
    event_locator VARCHAR(256) COLLATE "C" NOT NULL,
    provider VARCHAR(64) COLLATE "C" NOT NULL,
    observed_block_position BIGINT NOT NULL,
    observed_block_hash VARCHAR(256),
    source_event_time TIMESTAMPTZ(6),
    observed_at TIMESTAMPTZ(6) NOT NULL,
    payload TEXT NOT NULL,
    payload_hash VARCHAR(71) NOT NULL,
    parser_version VARCHAR(128) NOT NULL,
    ingested_at TIMESTAMPTZ(6) NOT NULL,
    CONSTRAINT raw_chain_events_pk
        PRIMARY KEY (chain_id, transaction_value, event_locator, provider),
    CONSTRAINT raw_chain_events_chain_id_caip2_check
        CHECK (chain_id ~ '^[-a-z0-9]{3,8}:[-_a-zA-Z0-9]{1,32}$'),
    CONSTRAINT raw_chain_events_transaction_value_format_check
        CHECK (transaction_value <> '' AND transaction_value = btrim(transaction_value)
            AND transaction_value !~ '[[:cntrl:]]'),
    CONSTRAINT raw_chain_events_transaction_value_utf8_length_check
        CHECK (octet_length(transaction_value) <= 512),
    CONSTRAINT raw_chain_events_event_locator_format_check
        CHECK (event_locator <> '' AND event_locator = btrim(event_locator)
            AND event_locator !~ '[[:cntrl:]]'),
    CONSTRAINT raw_chain_events_event_locator_utf8_length_check
        CHECK (octet_length(event_locator) <= 256),
    CONSTRAINT raw_chain_events_provider_format_check
        CHECK (provider <> '' AND provider = btrim(provider)
            AND provider !~ '[[:cntrl:]]'),
    CONSTRAINT raw_chain_events_provider_utf8_length_check
        CHECK (octet_length(provider) <= 64),
    CONSTRAINT raw_chain_events_observed_block_position_non_negative_check
        CHECK (observed_block_position >= 0),
    CONSTRAINT raw_chain_events_observed_block_hash_format_check
        CHECK (observed_block_hash IS NULL OR (observed_block_hash <> ''
            AND observed_block_hash = btrim(observed_block_hash)
            AND observed_block_hash !~ '[[:cntrl:]]')),
    CONSTRAINT raw_chain_events_observed_block_hash_utf8_length_check
        CHECK (observed_block_hash IS NULL OR octet_length(observed_block_hash) <= 256),
    CONSTRAINT raw_chain_events_payload_not_blank_check
        CHECK (payload !~ '^[[:space:]]*$'),
    CONSTRAINT raw_chain_events_payload_hash_format_check
        CHECK (payload_hash ~ '^sha256:[0-9a-f]{64}$'),
    CONSTRAINT raw_chain_events_parser_version_format_check
        CHECK (parser_version <> '' AND parser_version = btrim(parser_version)
            AND parser_version !~ '[[:cntrl:]]'),
    CONSTRAINT raw_chain_events_parser_version_utf8_length_check
        CHECK (octet_length(parser_version) <= 128)
);
