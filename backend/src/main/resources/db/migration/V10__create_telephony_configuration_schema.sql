CREATE TABLE call_directions (
    id UUID NOT NULL,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(80) NOT NULL,
    CONSTRAINT pk_call_directions PRIMARY KEY (id),
    CONSTRAINT uk_call_directions_code UNIQUE (code)
);

CREATE TABLE call_dispositions (
    id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    is_missed BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_call_dispositions PRIMARY KEY (id),
    CONSTRAINT uk_call_dispositions_code UNIQUE (code)
);

CREATE TABLE call_event_types (
    id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT pk_call_event_types PRIMARY KEY (id),
    CONSTRAINT uk_call_event_types_code UNIQUE (code)
);

CREATE TABLE webhook_statuses (
    id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    is_final BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_webhook_statuses PRIMARY KEY (id),
    CONSTRAINT uk_webhook_statuses_code UNIQUE (code)
);

CREATE TABLE pbx_configs (
    id UUID NOT NULL,
    organization_id UUID NOT NULL,
    branch_id UUID,
    name VARCHAR(120) NOT NULL,
    base_url VARCHAR(500) NOT NULL,
    credential_secret_reference VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_pbx_configs PRIMARY KEY (id),
    CONSTRAINT fk_pbx_configs_organization FOREIGN KEY (organization_id)
        REFERENCES organizations (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_pbx_configs_branch FOREIGN KEY (branch_id)
        REFERENCES branches (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_pbx_configs_organization_id ON pbx_configs (organization_id);
CREATE INDEX idx_pbx_configs_branch_id ON pbx_configs (branch_id);

CREATE TABLE extensions (
    id UUID NOT NULL,
    pbx_config_id UUID NOT NULL,
    user_id UUID,
    extension_number VARCHAR(30) NOT NULL,
    display_name VARCHAR(120),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_extensions PRIMARY KEY (id),
    CONSTRAINT uk_extensions_pbx_extension_number UNIQUE (pbx_config_id, extension_number),
    CONSTRAINT fk_extensions_pbx_config FOREIGN KEY (pbx_config_id)
        REFERENCES pbx_configs (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_extensions_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_extensions_user_id ON extensions (user_id);

CREATE TABLE sip_accounts (
    id UUID NOT NULL,
    pbx_config_id UUID NOT NULL,
    extension_id UUID NOT NULL,
    sip_username VARCHAR(120) NOT NULL,
    secret_reference VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_sip_accounts PRIMARY KEY (id),
    CONSTRAINT uk_sip_accounts_pbx_username UNIQUE (pbx_config_id, sip_username),
    CONSTRAINT fk_sip_accounts_pbx_config FOREIGN KEY (pbx_config_id)
        REFERENCES pbx_configs (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_sip_accounts_extension FOREIGN KEY (extension_id)
        REFERENCES extensions (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_sip_accounts_extension_id ON sip_accounts (extension_id);

CREATE TABLE phone_numbers (
    id UUID NOT NULL,
    pbx_config_id UUID NOT NULL,
    normalized_number VARCHAR(32) NOT NULL,
    display_number VARCHAR(50) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_phone_numbers PRIMARY KEY (id),
    CONSTRAINT uk_phone_numbers_pbx_normalized_number
        UNIQUE (pbx_config_id, normalized_number),
    CONSTRAINT fk_phone_numbers_pbx_config FOREIGN KEY (pbx_config_id)
        REFERENCES pbx_configs (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);
