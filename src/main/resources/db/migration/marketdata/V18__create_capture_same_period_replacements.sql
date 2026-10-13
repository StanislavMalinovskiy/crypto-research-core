ALTER TABLE marketdata.capture_deployments DROP CONSTRAINT capture_deployments_provider_period_key;

CREATE TABLE marketdata.capture_same_period_replacements (
    successor_deployment_key TEXT COLLATE "C" PRIMARY KEY REFERENCES marketdata.capture_deployments,
    predecessor_deployment_key TEXT COLLATE "C" NOT NULL UNIQUE REFERENCES marketdata.capture_deployments,
    allocation_root_key TEXT COLLATE "C" NOT NULL REFERENCES marketdata.capture_deployments,
    provider TEXT COLLATE "C" NOT NULL,
    account_period TEXT COLLATE "C" NOT NULL REFERENCES marketdata.capture_periods,
    version TEXT COLLATE "C" NOT NULL CHECK(version='continuous-same-period-replacement-v1'),
    predecessor_digest TEXT COLLATE "C" NOT NULL CHECK(predecessor_digest ~ '^sha256:[0-9a-f]{64}$'),
    root_digest TEXT COLLATE "C" NOT NULL CHECK(root_digest ~ '^sha256:[0-9a-f]{64}$'),
    successor_digest TEXT COLLATE "C" NOT NULL CHECK(successor_digest ~ '^sha256:[0-9a-f]{64}$'),
    predecessor_reason TEXT COLLATE "C" NOT NULL CHECK(octet_length(predecessor_reason) BETWEEN 1 AND 65536),
    decision_version TEXT COLLATE "C" NOT NULL CHECK(octet_length(decision_version) BETWEEN 1 AND 128),
    owner_decision TEXT COLLATE "C" NOT NULL CHECK(octet_length(owner_decision) BETWEEN 1 AND 4096),
    request_json TEXT NOT NULL CHECK(octet_length(request_json)<=1000000 AND jsonb_typeof(request_json::jsonb)='object'),
    request_digest TEXT COLLATE "C" NOT NULL CHECK(request_digest ~ '^sha256:[0-9a-f]{64}$'),
    applied_at TIMESTAMPTZ(6) NOT NULL,
    original_global_end TIMESTAMPTZ(6) NOT NULL,
    original_allowances BIGINT[] NOT NULL CHECK(array_ndims(original_allowances)=1 AND array_lower(original_allowances,1)=1 AND cardinality(original_allowances)=7 AND array_position(original_allowances,NULL) IS NULL AND 0<=ALL(original_allowances)),
    original_max_segments INTEGER NOT NULL CHECK(original_max_segments>0),
    carried BIGINT[] NOT NULL CHECK(array_ndims(carried)=1 AND array_lower(carried,1)=1 AND cardinality(carried)=7 AND array_position(carried,NULL) IS NULL AND 0<=ALL(carried)),
    consumed_segments INTEGER NOT NULL CHECK(consumed_segments>=0 AND consumed_segments<original_max_segments),
    CHECK(successor_deployment_key<>predecessor_deployment_key AND successor_deployment_key<>allocation_root_key),
    CHECK(original_global_end>applied_at),
    CHECK(carried[1]<=original_allowances[1] AND carried[2]<=original_allowances[2] AND carried[3]<=original_allowances[3]
        AND carried[4]<=original_allowances[4] AND carried[5]<=original_allowances[5] AND carried[6]<=original_allowances[6] AND carried[7]<=original_allowances[7])
);
