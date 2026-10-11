CREATE TABLE marketdata.capture_deployments (
    deployment_key TEXT COLLATE "C" PRIMARY KEY,
    provider TEXT COLLATE "C" NOT NULL UNIQUE,
    account_period TEXT COLLATE "C" NOT NULL REFERENCES marketdata.capture_periods,
    owner_authorization TEXT COLLATE "C" NOT NULL,
    configuration_json TEXT NOT NULL CHECK (octet_length(configuration_json)<=1000000),
    configuration_digest TEXT NOT NULL CHECK (configuration_digest ~ '^sha256:[0-9a-f]{64}$'),
    allowances BIGINT[] NOT NULL,
    reserved BIGINT[] NOT NULL,
    global_end TIMESTAMPTZ(6) NOT NULL,
    max_segments INTEGER NOT NULL CHECK(max_segments>0),
    state TEXT NOT NULL DEFAULT 'RUNNING',
    reason TEXT NOT NULL DEFAULT '',
    holder TEXT COLLATE "C" NOT NULL,
    generation BIGINT NOT NULL CHECK(generation>0),
    locked_until TIMESTAMPTZ(6) NOT NULL,
    CHECK(array_ndims(allowances)=1 AND array_lower(allowances,1)=1 AND cardinality(allowances)=7 AND array_position(allowances,NULL) IS NULL AND 0<=ALL(allowances)),
    CHECK(array_ndims(reserved)=1 AND array_lower(reserved,1)=1 AND cardinality(reserved)=7 AND array_position(reserved,NULL) IS NULL AND 0<=ALL(reserved)),
    CHECK(reserved[1]<=allowances[1] AND reserved[2]<=allowances[2] AND reserved[3]<=allowances[3]
        AND reserved[4]<=allowances[4] AND reserved[5]<=allowances[5] AND reserved[6]<=allowances[6] AND reserved[7]<=allowances[7])
);

CREATE TABLE marketdata.capture_segments (
    deployment_key TEXT COLLATE "C" NOT NULL REFERENCES marketdata.capture_deployments,
    ordinal INTEGER NOT NULL CHECK(ordinal>0),
    session_id UUID NOT NULL UNIQUE REFERENCES marketdata.capture_sessions,
    configuration_json TEXT NOT NULL CHECK(octet_length(configuration_json)<=1000000),
    configuration_digest TEXT NOT NULL CHECK(configuration_digest ~ '^sha256:[0-9a-f]{64}$'),
    allowances BIGINT[] NOT NULL,
    state TEXT NOT NULL DEFAULT 'CAPTURING',
    reason TEXT NOT NULL DEFAULT '',
    sealed_at TIMESTAMPTZ(6),
    decode_end TIMESTAMPTZ(6),
    cursor_signature TEXT COLLATE "C" NOT NULL DEFAULT '',
    last_batch TEXT COLLATE "C" REFERENCES marketdata.pumpswap_decode_batches(batch_key),
    source_count BIGINT NOT NULL DEFAULT 0 CHECK(source_count>=0),
    processed_count BIGINT NOT NULL DEFAULT 0 CHECK(processed_count>=0 AND processed_count<=source_count),
    page_count INTEGER NOT NULL DEFAULT 0 CHECK(page_count>=0),
    receipt_bytes BIGINT NOT NULL CHECK(receipt_bytes>0),
    receipts_json TEXT NOT NULL DEFAULT '[]',
    PRIMARY KEY(deployment_key,ordinal),
    CHECK(array_ndims(allowances)=1 AND array_lower(allowances,1)=1 AND cardinality(allowances)=7 AND array_position(allowances,NULL) IS NULL AND 0<=ALL(allowances)),
    CHECK(octet_length(receipts_json)<=receipt_bytes)
);
CREATE INDEX capture_segments_state_idx ON marketdata.capture_segments(deployment_key,state,ordinal);
