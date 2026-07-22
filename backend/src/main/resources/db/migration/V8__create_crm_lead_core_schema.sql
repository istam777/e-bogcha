CREATE TABLE leads (
    id UUID NOT NULL,
    branch_id UUID NOT NULL,
    source_id UUID NOT NULL,
    status_id UUID NOT NULL,
    owner_user_id UUID,
    parent_or_guardian_name VARCHAR(255) NOT NULL,
    preferred_language_id UUID,
    intent_summary TEXT,
    first_contact_due_at TIMESTAMPTZ NOT NULL,
    first_contact_at TIMESTAMPTZ,
    lost_reason_id UUID,
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID,
    CONSTRAINT pk_leads PRIMARY KEY (id),
    CONSTRAINT fk_leads_branch FOREIGN KEY (branch_id)
        REFERENCES branches (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_leads_source FOREIGN KEY (source_id)
        REFERENCES lead_sources (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_leads_status FOREIGN KEY (status_id)
        REFERENCES lead_statuses (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_leads_owner_user FOREIGN KEY (owner_user_id)
        REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_leads_preferred_language FOREIGN KEY (preferred_language_id)
        REFERENCES languages (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_leads_lost_reason FOREIGN KEY (lost_reason_id)
        REFERENCES lost_reasons (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_leads_created_by FOREIGN KEY (created_by)
        REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_leads_updated_by FOREIGN KEY (updated_by)
        REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_leads_branch_id ON leads (branch_id);
CREATE INDEX idx_leads_source_id ON leads (source_id);
CREATE INDEX idx_leads_status_id ON leads (status_id);
CREATE INDEX idx_leads_owner_user_id ON leads (owner_user_id);
CREATE INDEX idx_leads_preferred_language_id ON leads (preferred_language_id);
CREATE INDEX idx_leads_lost_reason_id ON leads (lost_reason_id);
CREATE INDEX idx_leads_created_by ON leads (created_by);
CREATE INDEX idx_leads_updated_by ON leads (updated_by);
CREATE INDEX idx_leads_branch_status ON leads (branch_id, status_id);
CREATE INDEX idx_leads_branch_created_at ON leads (branch_id, created_at);
CREATE INDEX idx_leads_first_contact_due_at ON leads (first_contact_due_at);

CREATE TABLE lead_phones (
    id UUID NOT NULL,
    lead_id UUID NOT NULL,
    normalized_phone VARCHAR(32) NOT NULL,
    display_phone VARCHAR(50) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_lead_phones PRIMARY KEY (id),
    CONSTRAINT fk_lead_phones_lead FOREIGN KEY (lead_id)
        REFERENCES leads (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_lead_phones_lead_id ON lead_phones (lead_id);
CREATE INDEX idx_lead_phones_normalized_phone ON lead_phones (normalized_phone);
CREATE UNIQUE INDEX ux_lead_phones_primary_lead
    ON lead_phones (lead_id)
    WHERE is_primary;

CREATE TABLE prospective_children (
    id UUID NOT NULL,
    lead_id UUID NOT NULL,
    first_name VARCHAR(120),
    last_name VARCHAR(120),
    patronymic VARCHAR(120),
    date_of_birth DATE,
    reported_age_months INTEGER,
    preferred_language_id UUID,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_prospective_children PRIMARY KEY (id),
    CONSTRAINT fk_prospective_children_lead FOREIGN KEY (lead_id)
        REFERENCES leads (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_prospective_children_preferred_language FOREIGN KEY (preferred_language_id)
        REFERENCES languages (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_prospective_children_lead_id ON prospective_children (lead_id);
CREATE INDEX idx_prospective_children_preferred_language_id
    ON prospective_children (preferred_language_id);
