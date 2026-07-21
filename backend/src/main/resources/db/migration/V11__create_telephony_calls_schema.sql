CREATE TABLE call_sessions (
    id UUID NOT NULL,
    pbx_config_id UUID NOT NULL,
    external_call_id VARCHAR(150) NOT NULL,
    direction_id UUID NOT NULL,
    disposition_id UUID,
    from_normalized_number VARCHAR(32) NOT NULL,
    to_normalized_number VARCHAR(32) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    answered_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    duration_seconds INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_call_sessions PRIMARY KEY (id),
    CONSTRAINT uk_call_sessions_pbx_external_call_id
        UNIQUE (pbx_config_id, external_call_id),
    CONSTRAINT fk_call_sessions_pbx_config FOREIGN KEY (pbx_config_id)
        REFERENCES pbx_configs (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_call_sessions_direction FOREIGN KEY (direction_id)
        REFERENCES call_directions (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_call_sessions_disposition FOREIGN KEY (disposition_id)
        REFERENCES call_dispositions (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_call_sessions_direction_id ON call_sessions (direction_id);
CREATE INDEX idx_call_sessions_disposition_id ON call_sessions (disposition_id);
CREATE INDEX idx_call_sessions_from_normalized_number
    ON call_sessions (from_normalized_number);
CREATE INDEX idx_call_sessions_to_normalized_number
    ON call_sessions (to_normalized_number);
CREATE INDEX idx_call_sessions_started_at ON call_sessions (started_at);

CREATE TABLE call_participants (
    id UUID NOT NULL,
    call_session_id UUID NOT NULL,
    user_id UUID,
    extension_id UUID,
    normalized_phone VARCHAR(32),
    participant_role VARCHAR(50) NOT NULL,
    joined_at TIMESTAMPTZ,
    left_at TIMESTAMPTZ,
    CONSTRAINT pk_call_participants PRIMARY KEY (id),
    CONSTRAINT fk_call_participants_call_session FOREIGN KEY (call_session_id)
        REFERENCES call_sessions (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_call_participants_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_call_participants_extension FOREIGN KEY (extension_id)
        REFERENCES extensions (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_call_participants_call_session_id
    ON call_participants (call_session_id);
CREATE INDEX idx_call_participants_user_id ON call_participants (user_id);
CREATE INDEX idx_call_participants_extension_id ON call_participants (extension_id);
CREATE INDEX idx_call_participants_normalized_phone
    ON call_participants (normalized_phone);

CREATE TABLE call_events (
    id UUID NOT NULL,
    call_session_id UUID NOT NULL,
    external_event_id VARCHAR(150),
    event_type_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    sanitized_metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_call_events PRIMARY KEY (id),
    CONSTRAINT fk_call_events_call_session FOREIGN KEY (call_session_id)
        REFERENCES call_sessions (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_call_events_event_type FOREIGN KEY (event_type_id)
        REFERENCES call_event_types (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_call_events_call_session_id ON call_events (call_session_id);
CREATE INDEX idx_call_events_external_event_id ON call_events (external_event_id);
CREATE INDEX idx_call_events_event_type_id ON call_events (event_type_id);
CREATE INDEX idx_call_events_occurred_at ON call_events (occurred_at);

CREATE TABLE call_recordings (
    id UUID NOT NULL,
    call_session_id UUID NOT NULL,
    stored_file_id UUID,
    external_recording_id VARCHAR(150),
    recording_url VARCHAR(1000),
    duration_seconds INTEGER,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_call_recordings PRIMARY KEY (id),
    CONSTRAINT fk_call_recordings_call_session FOREIGN KEY (call_session_id)
        REFERENCES call_sessions (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_call_recordings_stored_file FOREIGN KEY (stored_file_id)
        REFERENCES stored_files (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_call_recordings_call_session_id
    ON call_recordings (call_session_id);
CREATE INDEX idx_call_recordings_stored_file_id ON call_recordings (stored_file_id);
CREATE INDEX idx_call_recordings_external_recording_id
    ON call_recordings (external_recording_id);

CREATE TABLE lead_calls (
    lead_id UUID NOT NULL,
    call_session_id UUID NOT NULL,
    linked_by UUID,
    linked_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_lead_calls PRIMARY KEY (lead_id, call_session_id),
    CONSTRAINT fk_lead_calls_lead FOREIGN KEY (lead_id)
        REFERENCES leads (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_lead_calls_call_session FOREIGN KEY (call_session_id)
        REFERENCES call_sessions (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_lead_calls_linked_by FOREIGN KEY (linked_by)
        REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_lead_calls_call_session_id ON lead_calls (call_session_id);
CREATE INDEX idx_lead_calls_linked_by ON lead_calls (linked_by);

CREATE TABLE webhook_events (
    id UUID NOT NULL,
    pbx_config_id UUID NOT NULL,
    external_event_id VARCHAR(150) NOT NULL,
    status_id UUID NOT NULL,
    payload_hash VARCHAR(128) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    error_message TEXT,
    CONSTRAINT pk_webhook_events PRIMARY KEY (id),
    CONSTRAINT uk_webhook_events_pbx_external_event_id
        UNIQUE (pbx_config_id, external_event_id),
    CONSTRAINT fk_webhook_events_pbx_config FOREIGN KEY (pbx_config_id)
        REFERENCES pbx_configs (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_webhook_events_status FOREIGN KEY (status_id)
        REFERENCES webhook_statuses (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_webhook_events_status_id ON webhook_events (status_id);
CREATE INDEX idx_webhook_events_received_at ON webhook_events (received_at);
CREATE INDEX idx_webhook_events_payload_hash ON webhook_events (payload_hash);
