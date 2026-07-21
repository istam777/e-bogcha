CREATE TABLE departments (
    id UUID NOT NULL,
    organization_id UUID NOT NULL,
    branch_id UUID,
    parent_department_id UUID,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_departments PRIMARY KEY (id),
    CONSTRAINT uk_departments_organization_branch_code
        UNIQUE NULLS NOT DISTINCT (organization_id, branch_id, code),
    CONSTRAINT fk_departments_organization FOREIGN KEY (organization_id)
        REFERENCES organizations (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_departments_branch FOREIGN KEY (branch_id)
        REFERENCES branches (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_departments_parent FOREIGN KEY (parent_department_id)
        REFERENCES departments (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_departments_organization_id ON departments (organization_id);
CREATE INDEX idx_departments_branch_id ON departments (branch_id);
CREATE INDEX idx_departments_parent_department_id ON departments (parent_department_id);

CREATE TABLE positions (
    id UUID NOT NULL,
    organization_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_positions PRIMARY KEY (id),
    CONSTRAINT uk_positions_organization_code UNIQUE (organization_id, code),
    CONSTRAINT fk_positions_organization FOREIGN KEY (organization_id)
        REFERENCES organizations (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_positions_organization_id ON positions (organization_id);

CREATE TABLE user_credentials (
    user_id UUID NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    password_changed_at TIMESTAMPTZ NOT NULL,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_user_credentials PRIMARY KEY (user_id),
    CONSTRAINT fk_user_credentials_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE TABLE employees (
    id UUID NOT NULL,
    user_id UUID,
    branch_id UUID NOT NULL,
    department_id UUID,
    position_id UUID,
    employee_number VARCHAR(50) NOT NULL,
    employment_start_date DATE NOT NULL,
    employment_end_date DATE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_employees PRIMARY KEY (id),
    CONSTRAINT uk_employees_user_id UNIQUE (user_id),
    CONSTRAINT uk_employees_employee_number UNIQUE (employee_number),
    CONSTRAINT fk_employees_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_employees_branch FOREIGN KEY (branch_id)
        REFERENCES branches (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_employees_department FOREIGN KEY (department_id)
        REFERENCES departments (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_employees_position FOREIGN KEY (position_id)
        REFERENCES positions (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_employees_branch_id ON employees (branch_id);
CREATE INDEX idx_employees_department_id ON employees (department_id);
CREATE INDEX idx_employees_position_id ON employees (position_id);

CREATE TABLE user_branch_access (
    user_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    granted_by UUID NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_user_branch_access PRIMARY KEY (user_id, branch_id),
    CONSTRAINT fk_user_branch_access_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_user_branch_access_branch FOREIGN KEY (branch_id)
        REFERENCES branches (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_user_branch_access_granted_by FOREIGN KEY (granted_by)
        REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_user_branch_access_branch_id ON user_branch_access (branch_id);
CREATE INDEX idx_user_branch_access_granted_by ON user_branch_access (granted_by);

CREATE TABLE refresh_tokens (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    created_ip VARCHAR(64),
    user_agent TEXT,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);
