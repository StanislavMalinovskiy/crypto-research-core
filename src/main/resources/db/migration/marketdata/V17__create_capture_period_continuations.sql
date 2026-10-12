ALTER TABLE marketdata.capture_deployments DROP CONSTRAINT capture_deployments_provider_key;
ALTER TABLE marketdata.capture_deployments ADD CONSTRAINT capture_deployments_provider_period_key UNIQUE(provider,account_period);
CREATE UNIQUE INDEX capture_deployments_running_provider_idx ON marketdata.capture_deployments(provider) WHERE state='RUNNING';

CREATE TABLE marketdata.capture_period_continuations (
    successor_deployment_key TEXT COLLATE "C" PRIMARY KEY REFERENCES marketdata.capture_deployments,
    predecessor_deployment_key TEXT COLLATE "C" NOT NULL UNIQUE REFERENCES marketdata.capture_deployments,
    provider TEXT COLLATE "C" NOT NULL,
    account_period TEXT COLLATE "C" NOT NULL REFERENCES marketdata.capture_periods,
    version TEXT COLLATE "C" NOT NULL CHECK(version='continuous-period-continuation-v1'),
    period_start TIMESTAMPTZ(6) NOT NULL,
    period_end TIMESTAMPTZ(6) NOT NULL CHECK(period_end>period_start),
    predecessor_digest TEXT COLLATE "C" NOT NULL CHECK(predecessor_digest ~ '^sha256:[0-9a-f]{64}$'),
    request_json TEXT NOT NULL CHECK(octet_length(request_json)<=1000000),
    request_digest TEXT COLLATE "C" NOT NULL CHECK(request_digest ~ '^sha256:[0-9a-f]{64}$'),
    applied_at TIMESTAMPTZ(6) NOT NULL,
    baseline_source TEXT COLLATE "C" NOT NULL CHECK(baseline_source IN ('OWNER_REPORTED','INDEPENDENTLY_OBSERVED')),
    baseline_at TIMESTAMPTZ(6) NOT NULL,
    opening_hundredths BIGINT NOT NULL CHECK(opening_hundredths>=0),
    other_activity_hundredths BIGINT NOT NULL CHECK(other_activity_hundredths>=0),
    retained_opening_bytes BIGINT NOT NULL CHECK(retained_opening_bytes>=0),
    retained_ceiling_bytes BIGINT NOT NULL CHECK(retained_ceiling_bytes>=retained_opening_bytes),
    UNIQUE(provider,account_period),
    CHECK(baseline_at>=period_start AND baseline_at<period_end AND baseline_at<=applied_at)
);
