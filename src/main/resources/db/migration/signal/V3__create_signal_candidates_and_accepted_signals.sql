CREATE SCHEMA signal;

CREATE TABLE signal.signal_candidates (
    candidate_id VARCHAR(71) PRIMARY KEY,
    chain_id VARCHAR(41) COLLATE "C" NOT NULL,
    asset_address VARCHAR(256) COLLATE "C" NOT NULL,
    family VARCHAR(64) NOT NULL,
    detector_version VARCHAR(128) NOT NULL,
    configuration_fingerprint VARCHAR(71) NOT NULL,
    dataset_fingerprint VARCHAR(71) NOT NULL,
    window_start TIMESTAMPTZ(6) NOT NULL,
    decision_cutoff TIMESTAMPTZ(6) NOT NULL,
    status VARCHAR(16) NOT NULL,
    risk_decision VARCHAR(16),
    risk_manipulation_flags INTEGER,
    risk_lifecycle VARCHAR(32),
    risk_liquidity_usd NUMERIC(38, 8),
    risk_evidence_version VARCHAR(128),
    risk_evidence JSONB,
    evidence_fingerprint VARCHAR(71) NOT NULL,
    CONSTRAINT signal_candidates_dedup_unique UNIQUE (
        chain_id, asset_address, family, detector_version, configuration_fingerprint,
        dataset_fingerprint, window_start, decision_cutoff),
    CONSTRAINT signal_candidates_id_check CHECK (candidate_id ~ '^sha256:[0-9a-f]{64}$'),
    CONSTRAINT signal_candidates_family_check CHECK (family = 'LIQUIDITY_SPIKE'),
    CONSTRAINT signal_candidates_status_check CHECK (status IN ('DETECTED', 'REJECTED', 'ACCEPTED')),
    CONSTRAINT signal_candidates_risk_decision_check CHECK (
        risk_decision IS NULL OR risk_decision IN ('BLOCK', 'WATCH_ONLY', 'ALLOW')),
    CONSTRAINT signal_candidates_fingerprint_check CHECK (
        configuration_fingerprint ~ '^sha256:[0-9a-f]{64}$'
        AND dataset_fingerprint ~ '^sha256:[0-9a-f]{64}$'
        AND evidence_fingerprint ~ '^sha256:[0-9a-f]{64}$'),
    CONSTRAINT signal_candidates_window_check CHECK (window_start <= decision_cutoff),
    CONSTRAINT signal_candidates_risk_shape_check CHECK (
        (status = 'DETECTED' AND risk_decision IS NULL AND risk_evidence IS NULL)
        OR (status IN ('REJECTED', 'ACCEPTED') AND risk_decision IS NOT NULL
            AND risk_manipulation_flags IS NOT NULL AND risk_lifecycle IS NOT NULL
            AND risk_liquidity_usd IS NOT NULL AND risk_evidence_version IS NOT NULL
            AND risk_evidence IS NOT NULL))
);

CREATE TABLE signal.accepted_signals (
    signal_id VARCHAR(71) PRIMARY KEY,
    candidate_id VARCHAR(71) NOT NULL UNIQUE,
    chain_id VARCHAR(41) COLLATE "C" NOT NULL,
    asset_address VARCHAR(256) COLLATE "C" NOT NULL,
    family VARCHAR(64) NOT NULL,
    position_type VARCHAR(16) NOT NULL,
    available_at TIMESTAMPTZ(6) NOT NULL,
    dataset_fingerprint VARCHAR(71) NOT NULL,
    baseline_transaction_value VARCHAR(512) COLLATE "C" NOT NULL,
    baseline_event_locator VARCHAR(256) COLLATE "C" NOT NULL,
    current_transaction_value VARCHAR(512) COLLATE "C" NOT NULL,
    current_event_locator VARCHAR(256) COLLATE "C" NOT NULL,
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
    evidence_fingerprint VARCHAR(71) NOT NULL,
    CONSTRAINT accepted_signals_candidate_fk FOREIGN KEY (candidate_id)
        REFERENCES signal.signal_candidates (candidate_id),
    CONSTRAINT accepted_signals_id_check CHECK (signal_id ~ '^sha256:[0-9a-f]{64}$'),
    CONSTRAINT accepted_signals_family_check CHECK (family = 'LIQUIDITY_SPIKE'),
    CONSTRAINT accepted_signals_position_check CHECK (position_type = 'ENTRY'),
    CONSTRAINT accepted_signals_risk_check CHECK (risk_decision = 'ALLOW'),
    CONSTRAINT accepted_signals_score_check CHECK (score BETWEEN 0 AND 100),
    CONSTRAINT accepted_signals_confidence_check CHECK (confidence BETWEEN 0.0000 AND 1.0000),
    CONSTRAINT accepted_signals_fingerprint_check CHECK (
        dataset_fingerprint ~ '^sha256:[0-9a-f]{64}$'
        AND configuration_fingerprint ~ '^sha256:[0-9a-f]{64}$'
        AND evidence_fingerprint ~ '^sha256:[0-9a-f]{64}$')
);
