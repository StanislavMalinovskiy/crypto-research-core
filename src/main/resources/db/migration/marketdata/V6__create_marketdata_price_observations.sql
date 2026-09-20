CREATE TABLE marketdata.price_observations (
    chain_id VARCHAR(41) COLLATE "C" NOT NULL,
    transaction_value VARCHAR(512) COLLATE "C" NOT NULL,
    event_locator VARCHAR(256) COLLATE "C" NOT NULL,
    asset_address VARCHAR(256) COLLATE "C" NOT NULL,
    quote_asset_address VARCHAR(256) COLLATE "C" NOT NULL,
    venue VARCHAR(128) COLLATE "C" NOT NULL,
    price NUMERIC(38, 18) NOT NULL,
    trade_notional_quote NUMERIC(38, 8) NOT NULL,
    observed_block_position BIGINT NOT NULL,
    observed_at TIMESTAMPTZ(6) NOT NULL,
    confidence NUMERIC(5, 4) NOT NULL,
    provider VARCHAR(64) COLLATE "C" NOT NULL,
    source_event_time TIMESTAMPTZ(6),
    CONSTRAINT price_observations_pk
        PRIMARY KEY (chain_id, transaction_value, event_locator),
    CONSTRAINT price_observations_chain_id_caip2_check
        CHECK (chain_id ~ '^[-a-z0-9]{3,8}:[-_a-zA-Z0-9]{1,32}$'),
    CONSTRAINT price_observations_transaction_value_format_check
        CHECK (transaction_value <> '' AND transaction_value = btrim(transaction_value)
            AND transaction_value !~ '[[:cntrl:]]'),
    CONSTRAINT price_observations_event_locator_format_check
        CHECK (event_locator <> '' AND event_locator = btrim(event_locator)
            AND event_locator !~ '[[:cntrl:]]'),
    CONSTRAINT price_observations_asset_format_check
        CHECK (asset_address <> '' AND asset_address = btrim(asset_address)
            AND asset_address !~ '[[:cntrl:]]'),
    CONSTRAINT price_observations_quote_asset_format_check
        CHECK (quote_asset_address <> '' AND quote_asset_address = btrim(quote_asset_address)
            AND quote_asset_address !~ '[[:cntrl:]]'),
    CONSTRAINT price_observations_venue_format_check
        CHECK (venue <> '' AND venue = btrim(venue) AND venue !~ '[[:cntrl:]]'),
    CONSTRAINT price_observations_provider_format_check
        CHECK (provider <> '' AND provider = btrim(provider) AND provider !~ '[[:cntrl:]]'),
    CONSTRAINT price_observations_transaction_value_utf8_length_check
        CHECK (octet_length(transaction_value) <= 512),
    CONSTRAINT price_observations_event_locator_utf8_length_check
        CHECK (octet_length(event_locator) <= 256),
    CONSTRAINT price_observations_asset_utf8_length_check
        CHECK (octet_length(asset_address) <= 256),
    CONSTRAINT price_observations_quote_asset_utf8_length_check
        CHECK (octet_length(quote_asset_address) <= 256),
    CONSTRAINT price_observations_venue_utf8_length_check
        CHECK (octet_length(venue) <= 128),
    CONSTRAINT price_observations_provider_utf8_length_check
        CHECK (octet_length(provider) <= 64),
    CONSTRAINT price_observations_price_positive_check
        CHECK (price > 0),
    CONSTRAINT price_observations_notional_non_negative_check
        CHECK (trade_notional_quote >= 0),
    CONSTRAINT price_observations_block_position_non_negative_check
        CHECK (observed_block_position >= 0),
    CONSTRAINT price_observations_confidence_range_check
        CHECK (confidence >= 0.0000 AND confidence <= 1.0000)
);

CREATE INDEX price_observations_point_in_time_idx
    ON marketdata.price_observations (
        chain_id, asset_address, observed_at, transaction_value, event_locator);
