CREATE TABLE organizations (
    id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_organizations PRIMARY KEY (id),
    CONSTRAINT uk_organizations_code UNIQUE (code)
);

CREATE TABLE branches (
    id UUID NOT NULL,
    organization_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    address_text TEXT,
    normalized_phone VARCHAR(32),
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Tashkent',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_branches PRIMARY KEY (id),
    CONSTRAINT uk_branches_organization_code UNIQUE (organization_id, code),
    CONSTRAINT fk_branches_organization FOREIGN KEY (organization_id)
        REFERENCES organizations (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_branches_organization_id ON branches (organization_id);

CREATE TABLE user_statuses (
    id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT pk_user_statuses PRIMARY KEY (id),
    CONSTRAINT uk_user_statuses_code UNIQUE (code)
);

CREATE TABLE users (
    id UUID NOT NULL,
    organization_id UUID NOT NULL,
    username_normalized VARCHAR(150) NOT NULL,
    email_normalized VARCHAR(255),
    normalized_phone VARCHAR(32),
    display_name VARCHAR(255) NOT NULL,
    status_id UUID NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_organization_username UNIQUE (organization_id, username_normalized),
    CONSTRAINT uk_users_organization_email UNIQUE (organization_id, email_normalized),
    CONSTRAINT fk_users_organization FOREIGN KEY (organization_id)
        REFERENCES organizations (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_users_status FOREIGN KEY (status_id)
        REFERENCES user_statuses (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_users_created_by FOREIGN KEY (created_by)
        REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_users_updated_by FOREIGN KEY (updated_by)
        REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_users_organization_id ON users (organization_id);
CREATE INDEX idx_users_status_id ON users (status_id);
CREATE INDEX idx_users_normalized_phone ON users (normalized_phone);
CREATE INDEX idx_users_created_by ON users (created_by);
CREATE INDEX idx_users_updated_by ON users (updated_by);

CREATE TABLE roles (
    id UUID NOT NULL,
    organization_id UUID NOT NULL,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uk_roles_organization_code UNIQUE (organization_id, code),
    CONSTRAINT fk_roles_organization FOREIGN KEY (organization_id)
        REFERENCES organizations (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_roles_organization_id ON roles (organization_id);

CREATE TABLE permissions (
    id UUID NOT NULL,
    code VARCHAR(150) NOT NULL,
    module_code VARCHAR(80) NOT NULL,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_permissions PRIMARY KEY (id),
    CONSTRAINT uk_permissions_code UNIQUE (code)
);

CREATE INDEX idx_permissions_module_code ON permissions (module_code);

CREATE TABLE role_permissions (
    role_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL,
    granted_by UUID,
    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id)
        REFERENCES roles (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id)
        REFERENCES permissions (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_role_permissions_granted_by FOREIGN KEY (granted_by)
        REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_role_permissions_permission_id ON role_permissions (permission_id);
CREATE INDEX idx_role_permissions_granted_by ON role_permissions (granted_by);

CREATE TABLE user_roles (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    branch_id UUID,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ,
    assigned_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id)
        REFERENCES roles (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_user_roles_branch FOREIGN KEY (branch_id)
        REFERENCES branches (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_user_roles_assigned_by FOREIGN KEY (assigned_by)
        REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_user_roles_user_id ON user_roles (user_id);
CREATE INDEX idx_user_roles_role_id ON user_roles (role_id);
CREATE INDEX idx_user_roles_branch_id ON user_roles (branch_id);
CREATE INDEX idx_user_roles_assigned_by ON user_roles (assigned_by);
CREATE INDEX idx_user_roles_assignment_history
    ON user_roles (user_id, role_id, branch_id, valid_from);
CREATE UNIQUE INDEX ux_user_roles_current_assignment
    ON user_roles (user_id, role_id, branch_id) NULLS NOT DISTINCT
    WHERE valid_until IS NULL;

CREATE TABLE audit_logs (
    id UUID NOT NULL,
    organization_id UUID NOT NULL,
    branch_id UUID,
    actor_user_id UUID,
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(80) NOT NULL,
    correlation_id VARCHAR(100),
    request_id VARCHAR(100),
    changed_field_names JSONB,
    sanitized_metadata JSONB,
    ip_address VARCHAR(64),
    user_agent TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_audit_logs PRIMARY KEY (id),
    CONSTRAINT fk_audit_logs_organization FOREIGN KEY (organization_id)
        REFERENCES organizations (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_audit_logs_branch FOREIGN KEY (branch_id)
        REFERENCES branches (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_audit_logs_actor_user FOREIGN KEY (actor_user_id)
        REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_audit_logs_organization_id ON audit_logs (organization_id);
CREATE INDEX idx_audit_logs_branch_id ON audit_logs (branch_id);
CREATE INDEX idx_audit_logs_actor_user_id ON audit_logs (actor_user_id);
CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at);
CREATE INDEX idx_audit_logs_correlation_id ON audit_logs (correlation_id);
CREATE INDEX idx_audit_logs_request_id ON audit_logs (request_id);
