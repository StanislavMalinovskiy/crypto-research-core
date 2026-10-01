CREATE TABLE signal.v2_signal_candidates (
    candidate_id VARCHAR(71) PRIMARY KEY,
    chain_id VARCHAR(41) COLLATE "C" NOT NULL,
    asset_address VARCHAR(256) COLLATE "C" NOT NULL,
    family VARCHAR(64) NOT NULL,
    detector_version VARCHAR(128) NOT NULL,
    configuration_fingerprint VARCHAR(71) NOT NULL,
    decision_dataset_fingerprint VARCHAR(71) NOT NULL,
    window_start TIMESTAMPTZ(6) NOT NULL,
    decision_cutoff TIMESTAMPTZ(6) NOT NULL,
    status VARCHAR(16) NOT NULL,
    risk_decision VARCHAR(16),
    risk_manipulation_flags INTEGER,
    risk_lifecycle VARCHAR(32),
    risk_liquidity_usd NUMERIC(38, 8),
    risk_evidence_version VARCHAR(128),
    risk_evidence JSONB,
    CONSTRAINT v2_signal_candidates_id_check CHECK (candidate_id ~ '^sha256:[0-9a-f]{64}$'),
    CONSTRAINT v2_signal_candidates_fingerprints_check CHECK (
        configuration_fingerprint ~ '^sha256:[0-9a-f]{64}$'
        AND decision_dataset_fingerprint ~ '^sha256:[0-9a-f]{64}$'),
    CONSTRAINT v2_signal_candidates_status_check CHECK (status IN ('DETECTED', 'REJECTED', 'ACCEPTED')),
    CONSTRAINT v2_signal_candidates_risk_shape_check CHECK (
        (status = 'DETECTED' AND risk_decision IS NULL AND risk_evidence IS NULL)
        OR (status IN ('REJECTED', 'ACCEPTED') AND risk_decision IS NOT NULL
            AND risk_manipulation_flags IS NOT NULL AND risk_lifecycle IS NOT NULL
            AND risk_liquidity_usd IS NOT NULL AND risk_evidence_version IS NOT NULL
            AND risk_evidence IS NOT NULL))
);

CREATE TABLE signal.v2_accepted_signals (
    signal_id VARCHAR(71) PRIMARY KEY,
    candidate_id VARCHAR(71) NOT NULL UNIQUE REFERENCES signal.v2_signal_candidates (candidate_id),
    chain_id VARCHAR(41) COLLATE "C" NOT NULL,
    asset_address VARCHAR(256) COLLATE "C" NOT NULL,
    family VARCHAR(64) NOT NULL,
    position_type VARCHAR(16) NOT NULL,
    available_at TIMESTAMPTZ(6) NOT NULL,
    decision_cutoff TIMESTAMPTZ(6) NOT NULL,
    decision_dataset_fingerprint VARCHAR(71) NOT NULL,
    baseline_transaction_value VARCHAR(512) COLLATE "C" NOT NULL,
    baseline_event_locator VARCHAR(256) COLLATE "C" NOT NULL,
    baseline_revision_key VARCHAR(71) NOT NULL,
    current_transaction_value VARCHAR(512) COLLATE "C" NOT NULL,
    current_event_locator VARCHAR(256) COLLATE "C" NOT NULL,
    current_revision_key VARCHAR(71) NOT NULL,
    risk_decision VARCHAR(16) NOT NULL,
    risk_manipulation_flags INTEGER NOT NULL,
    risk_lifecycle VARCHAR(32) NOT NULL,
    risk_liquidity_usd NUMERIC(38, 8) NOT NULL,
    risk_evidence_version VARCHAR(128) NOT NULL,
    risk_evidence JSONB NOT NULL,
    detector_version VARCHAR(128) NOT NULL,
    scorer_version VARCHAR(128) NOT NULL,
    configuration_fingerprint VARCHAR(71) NOT NULL,
    score INTEGER NOT NULL,
    grade VARCHAR(8) NOT NULL,
    confidence NUMERIC(5, 4) NOT NULL,
    reasoning JSONB NOT NULL,
    CONSTRAINT v2_accepted_signals_id_check CHECK (signal_id ~ '^sha256:[0-9a-f]{64}$'),
    CONSTRAINT v2_accepted_signals_fingerprints_check CHECK (
        decision_dataset_fingerprint ~ '^sha256:[0-9a-f]{64}$'
        AND configuration_fingerprint ~ '^sha256:[0-9a-f]{64}$'
        AND baseline_revision_key ~ '^sha256:[0-9a-f]{64}$'
        AND current_revision_key ~ '^sha256:[0-9a-f]{64}$'),
    CONSTRAINT v2_accepted_signals_risk_check CHECK (risk_decision = 'ALLOW'),
    CONSTRAINT v2_accepted_signals_cutoff_check CHECK (available_at = decision_cutoff),
    CONSTRAINT v2_accepted_signals_score_check CHECK (score BETWEEN 0 AND 100
        AND confidence BETWEEN 0.0000 AND 1.0000)
);
