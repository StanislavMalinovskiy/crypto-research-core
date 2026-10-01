CREATE TABLE evaluation.v2_evaluation_runs (
    run_id VARCHAR(71) PRIMARY KEY,
    signal_id VARCHAR(71) NOT NULL,
    horizon VARCHAR(16) NOT NULL,
    build_identity VARCHAR(256) NOT NULL,
    source_revision VARCHAR(256) NOT NULL,
    source_dirty BOOLEAN NOT NULL,
    algorithm_version VARCHAR(128) NOT NULL,
    configuration_fingerprint VARCHAR(71) NOT NULL,
    decision_dataset_fingerprint VARCHAR(71) NOT NULL,
    evaluation_dataset_fingerprint VARCHAR(71) NOT NULL,
    evaluation_cutoff TIMESTAMPTZ(6) NOT NULL,
    random_seed BIGINT,
    evidence_fingerprint VARCHAR(71) NOT NULL,
    CONSTRAINT v2_evaluation_runs_horizon_check CHECK (horizon = '1h'),
    CONSTRAINT v2_evaluation_runs_required_text_check CHECK (
        build_identity <> '' AND source_revision <> '' AND algorithm_version <> ''),
    CONSTRAINT v2_evaluation_runs_fingerprints_check CHECK (
        run_id ~ '^sha256:[0-9a-f]{64}$'
        AND configuration_fingerprint ~ '^sha256:[0-9a-f]{64}$'
        AND decision_dataset_fingerprint ~ '^sha256:[0-9a-f]{64}$'
        AND evaluation_dataset_fingerprint ~ '^sha256:[0-9a-f]{64}$'
        AND evidence_fingerprint ~ '^sha256:[0-9a-f]{64}$')
);

CREATE TABLE evaluation.v2_entry_outcomes (
    outcome_id VARCHAR(71) PRIMARY KEY,
    run_id VARCHAR(71) NOT NULL REFERENCES evaluation.v2_evaluation_runs (run_id),
    signal_id VARCHAR(71) NOT NULL,
    horizon VARCHAR(16) NOT NULL,
    pricing_status VARCHAR(16) NOT NULL,
    missing_price_reason VARCHAR(128),
    entry_revision_key VARCHAR(71),
    horizon_revision_key VARCHAR(71),
    entry_price_usd NUMERIC(38, 18),
    entry_liquidity_usd NUMERIC(38, 8),
    entry_confidence NUMERIC(5, 4),
    entry_observed_at TIMESTAMPTZ(6),
    horizon_price_usd NUMERIC(38, 18),
    horizon_liquidity_usd NUMERIC(38, 8),
    horizon_confidence NUMERIC(5, 4),
    horizon_observed_at TIMESTAMPTZ(6),
    gross_return NUMERIC(18, 8),
    friction NUMERIC(18, 8),
    net_return NUMERIC(18, 8),
    evidence JSONB NOT NULL,
    evidence_fingerprint VARCHAR(71) NOT NULL,
    CONSTRAINT v2_entry_outcomes_run_unique UNIQUE (run_id, signal_id, horizon),
    CONSTRAINT v2_entry_outcomes_horizon_check CHECK (horizon = '1h'),
    CONSTRAINT v2_entry_outcomes_status_check CHECK (pricing_status IN ('PRICED', 'UNPRICED')),
    CONSTRAINT v2_entry_outcomes_fingerprints_check CHECK (
        outcome_id ~ '^sha256:[0-9a-f]{64}$'
        AND evidence_fingerprint ~ '^sha256:[0-9a-f]{64}$'
        AND (entry_revision_key IS NULL OR entry_revision_key ~ '^sha256:[0-9a-f]{64}$')
        AND (horizon_revision_key IS NULL OR horizon_revision_key ~ '^sha256:[0-9a-f]{64}$')),
    CONSTRAINT v2_entry_outcomes_shape_check CHECK (
        (pricing_status = 'PRICED' AND missing_price_reason IS NULL
            AND entry_revision_key IS NOT NULL AND horizon_revision_key IS NOT NULL
            AND entry_price_usd IS NOT NULL AND horizon_price_usd IS NOT NULL
            AND gross_return IS NOT NULL AND friction IS NOT NULL AND net_return IS NOT NULL)
        OR (pricing_status = 'UNPRICED' AND missing_price_reason IS NOT NULL
            AND gross_return IS NULL AND friction IS NULL AND net_return IS NULL))
);

CREATE TABLE evaluation.v2_evaluation_reports (
    report_id VARCHAR(71) PRIMARY KEY,
    run_id VARCHAR(71) NOT NULL UNIQUE REFERENCES evaluation.v2_evaluation_runs (run_id),
    family VARCHAR(64) NOT NULL,
    signal_count INTEGER NOT NULL,
    priced_outcome_count INTEGER NOT NULL,
    unpriced_outcome_count INTEGER NOT NULL,
    average_net_return NUMERIC(18, 8),
    ordered_content JSONB NOT NULL,
    report_fingerprint VARCHAR(71) NOT NULL,
    CONSTRAINT v2_evaluation_reports_counts_check CHECK (
        signal_count >= 0 AND priced_outcome_count >= 0 AND unpriced_outcome_count >= 0
        AND priced_outcome_count + unpriced_outcome_count = signal_count),
    CONSTRAINT v2_evaluation_reports_average_shape_check CHECK (
        (priced_outcome_count = 0 AND average_net_return IS NULL)
        OR (priced_outcome_count > 0 AND average_net_return IS NOT NULL)),
    CONSTRAINT v2_evaluation_reports_fingerprints_check CHECK (
        report_id ~ '^sha256:[0-9a-f]{64}$' AND report_fingerprint ~ '^sha256:[0-9a-f]{64}$')
);
