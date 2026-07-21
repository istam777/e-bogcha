CREATE TABLE lead_sources (
    id UUID NOT NULL,
    organization_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(120) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT pk_lead_sources PRIMARY KEY (id),
    CONSTRAINT uk_lead_sources_organization_code UNIQUE (organization_id, code),
    CONSTRAINT fk_lead_sources_organization FOREIGN KEY (organization_id)
        REFERENCES organizations (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_lead_sources_organization_id ON lead_sources (organization_id);

CREATE TABLE lead_statuses (
    id UUID NOT NULL,
    organization_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(120) NOT NULL,
    pipeline_order INTEGER NOT NULL,
    is_initial BOOLEAN NOT NULL DEFAULT FALSE,
    is_success BOOLEAN NOT NULL DEFAULT FALSE,
    is_lost BOOLEAN NOT NULL DEFAULT FALSE,
    is_archived BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_lead_statuses PRIMARY KEY (id),
    CONSTRAINT uk_lead_statuses_organization_code UNIQUE (organization_id, code),
    CONSTRAINT fk_lead_statuses_organization FOREIGN KEY (organization_id)
        REFERENCES organizations (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_lead_statuses_organization_id ON lead_statuses (organization_id);
CREATE INDEX idx_lead_statuses_pipeline_order ON lead_statuses (pipeline_order);

CREATE TABLE lost_reasons (
    id UUID NOT NULL,
    organization_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(120) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_lost_reasons PRIMARY KEY (id),
    CONSTRAINT uk_lost_reasons_organization_code UNIQUE (organization_id, code),
    CONSTRAINT fk_lost_reasons_organization FOREIGN KEY (organization_id)
        REFERENCES organizations (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_lost_reasons_organization_id ON lost_reasons (organization_id);

CREATE TABLE tour_outcomes (
    id UUID NOT NULL,
    organization_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(120) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_tour_outcomes PRIMARY KEY (id),
    CONSTRAINT uk_tour_outcomes_organization_code UNIQUE (organization_id, code),
    CONSTRAINT fk_tour_outcomes_organization FOREIGN KEY (organization_id)
        REFERENCES organizations (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_tour_outcomes_organization_id ON tour_outcomes (organization_id);

CREATE TABLE lead_task_statuses (
    id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    is_closed BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_lead_task_statuses PRIMARY KEY (id),
    CONSTRAINT uk_lead_task_statuses_code UNIQUE (code)
);

CREATE TABLE lead_activity_types (
    id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_lead_activity_types PRIMARY KEY (id),
    CONSTRAINT uk_lead_activity_types_code UNIQUE (code)
);
