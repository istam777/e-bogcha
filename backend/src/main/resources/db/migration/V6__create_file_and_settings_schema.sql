CREATE TABLE stored_files (
    id UUID NOT NULL,
    organization_id UUID NOT NULL,
    branch_id UUID,
    storage_provider VARCHAR(40) NOT NULL,
    bucket_name VARCHAR(120) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(150) NOT NULL,
    file_size BIGINT NOT NULL,
    checksum VARCHAR(128),
    uploaded_by UUID,
    uploaded_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT pk_stored_files PRIMARY KEY (id),
    CONSTRAINT uk_stored_files_storage_object UNIQUE (storage_provider, bucket_name, object_key),
    CONSTRAINT fk_stored_files_organization FOREIGN KEY (organization_id)
        REFERENCES organizations (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_stored_files_branch FOREIGN KEY (branch_id)
        REFERENCES branches (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_stored_files_uploaded_by FOREIGN KEY (uploaded_by)
        REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_stored_files_organization_id ON stored_files (organization_id);
CREATE INDEX idx_stored_files_branch_id ON stored_files (branch_id);
CREATE INDEX idx_stored_files_uploaded_by ON stored_files (uploaded_by);
CREATE INDEX idx_stored_files_checksum ON stored_files (checksum);

CREATE TABLE application_settings (
    id UUID NOT NULL,
    organization_id UUID NOT NULL,
    branch_id UUID,
    setting_key VARCHAR(150) NOT NULL,
    setting_value TEXT,
    value_type VARCHAR(40) NOT NULL,
    is_sensitive BOOLEAN NOT NULL DEFAULT FALSE,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_application_settings PRIMARY KEY (id),
    CONSTRAINT uk_application_settings_organization_branch_key
        UNIQUE NULLS NOT DISTINCT (organization_id, branch_id, setting_key),
    CONSTRAINT fk_application_settings_organization FOREIGN KEY (organization_id)
        REFERENCES organizations (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_application_settings_branch FOREIGN KEY (branch_id)
        REFERENCES branches (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_application_settings_updated_by FOREIGN KEY (updated_by)
        REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_application_settings_organization_id ON application_settings (organization_id);
CREATE INDEX idx_application_settings_branch_id ON application_settings (branch_id);
CREATE INDEX idx_application_settings_updated_by ON application_settings (updated_by);

CREATE TABLE number_sequences (
    id UUID NOT NULL,
    organization_id UUID NOT NULL,
    branch_id UUID,
    sequence_code VARCHAR(80) NOT NULL,
    year INTEGER,
    prefix VARCHAR(30),
    next_value BIGINT NOT NULL,
    padding_length INTEGER NOT NULL DEFAULT 6,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_number_sequences PRIMARY KEY (id),
    CONSTRAINT uk_number_sequences_scope_code_year
        UNIQUE NULLS NOT DISTINCT (organization_id, branch_id, sequence_code, year),
    CONSTRAINT fk_number_sequences_organization FOREIGN KEY (organization_id)
        REFERENCES organizations (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_number_sequences_branch FOREIGN KEY (branch_id)
        REFERENCES branches (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_number_sequences_organization_id ON number_sequences (organization_id);
CREATE INDEX idx_number_sequences_branch_id ON number_sequences (branch_id);
