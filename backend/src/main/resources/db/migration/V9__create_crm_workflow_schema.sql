CREATE TABLE lead_assignments (
    id UUID NOT NULL,
    lead_id UUID NOT NULL,
    assigned_user_id UUID NOT NULL,
    assigned_by UUID,
    assigned_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    assignment_reason TEXT,
    CONSTRAINT pk_lead_assignments PRIMARY KEY (id),
    CONSTRAINT fk_lead_assignments_lead FOREIGN KEY (lead_id)
        REFERENCES leads (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_lead_assignments_assigned_user FOREIGN KEY (assigned_user_id)
        REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_lead_assignments_assigned_by FOREIGN KEY (assigned_by)
        REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_lead_assignments_lead_id ON lead_assignments (lead_id);
CREATE INDEX idx_lead_assignments_assigned_user_id ON lead_assignments (assigned_user_id);
CREATE INDEX idx_lead_assignments_assigned_by ON lead_assignments (assigned_by);
CREATE INDEX idx_lead_assignments_lead_ended_at ON lead_assignments (lead_id, ended_at);
CREATE UNIQUE INDEX ux_lead_assignments_active_lead
    ON lead_assignments (lead_id)
    WHERE ended_at IS NULL;

CREATE TABLE lead_status_history (
    id UUID NOT NULL,
    lead_id UUID NOT NULL,
    from_status_id UUID,
    to_status_id UUID NOT NULL,
    changed_by UUID NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL,
    note TEXT,
    CONSTRAINT pk_lead_status_history PRIMARY KEY (id),
    CONSTRAINT fk_lead_status_history_lead FOREIGN KEY (lead_id)
        REFERENCES leads (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_lead_status_history_from_status FOREIGN KEY (from_status_id)
        REFERENCES lead_statuses (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_lead_status_history_to_status FOREIGN KEY (to_status_id)
        REFERENCES lead_statuses (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_lead_status_history_changed_by FOREIGN KEY (changed_by)
        REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_lead_status_history_lead_id ON lead_status_history (lead_id);
CREATE INDEX idx_lead_status_history_from_status_id ON lead_status_history (from_status_id);
CREATE INDEX idx_lead_status_history_to_status_id ON lead_status_history (to_status_id);
CREATE INDEX idx_lead_status_history_changed_by ON lead_status_history (changed_by);
CREATE INDEX idx_lead_status_history_changed_at ON lead_status_history (changed_at);

CREATE TABLE lead_activities (
    id UUID NOT NULL,
    lead_id UUID NOT NULL,
    activity_type_id UUID NOT NULL,
    performed_by UUID,
    occurred_at TIMESTAMPTZ NOT NULL,
    summary TEXT NOT NULL,
    sanitized_metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_lead_activities PRIMARY KEY (id),
    CONSTRAINT fk_lead_activities_lead FOREIGN KEY (lead_id)
        REFERENCES leads (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_lead_activities_activity_type FOREIGN KEY (activity_type_id)
        REFERENCES lead_activity_types (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_lead_activities_performed_by FOREIGN KEY (performed_by)
        REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_lead_activities_lead_id ON lead_activities (lead_id);
CREATE INDEX idx_lead_activities_activity_type_id ON lead_activities (activity_type_id);
CREATE INDEX idx_lead_activities_performed_by ON lead_activities (performed_by);
CREATE INDEX idx_lead_activities_occurred_at ON lead_activities (occurred_at);

CREATE TABLE lead_notes (
    id UUID NOT NULL,
    lead_id UUID NOT NULL,
    author_user_id UUID NOT NULL,
    note_text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_lead_notes PRIMARY KEY (id),
    CONSTRAINT fk_lead_notes_lead FOREIGN KEY (lead_id)
        REFERENCES leads (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_lead_notes_author_user FOREIGN KEY (author_user_id)
        REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_lead_notes_lead_id ON lead_notes (lead_id);
CREATE INDEX idx_lead_notes_author_user_id ON lead_notes (author_user_id);

CREATE TABLE lead_tasks (
    id UUID NOT NULL,
    lead_id UUID NOT NULL,
    assigned_to_user_id UUID NOT NULL,
    status_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    due_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_lead_tasks PRIMARY KEY (id),
    CONSTRAINT fk_lead_tasks_lead FOREIGN KEY (lead_id)
        REFERENCES leads (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_lead_tasks_assigned_to_user FOREIGN KEY (assigned_to_user_id)
        REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_lead_tasks_status FOREIGN KEY (status_id)
        REFERENCES lead_task_statuses (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_lead_tasks_created_by FOREIGN KEY (created_by)
        REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_lead_tasks_lead_id ON lead_tasks (lead_id);
CREATE INDEX idx_lead_tasks_assigned_to_user_id ON lead_tasks (assigned_to_user_id);
CREATE INDEX idx_lead_tasks_status_id ON lead_tasks (status_id);
CREATE INDEX idx_lead_tasks_created_by ON lead_tasks (created_by);
CREATE INDEX idx_lead_tasks_due_at ON lead_tasks (due_at);
CREATE INDEX idx_lead_tasks_assignee_status_due
    ON lead_tasks (assigned_to_user_id, status_id, due_at);

CREATE TABLE tours (
    id UUID NOT NULL,
    lead_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    sales_manager_user_id UUID NOT NULL,
    scheduled_at TIMESTAMPTZ NOT NULL,
    attended_at TIMESTAMPTZ,
    outcome_id UUID,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_tours PRIMARY KEY (id),
    CONSTRAINT fk_tours_lead FOREIGN KEY (lead_id)
        REFERENCES leads (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_tours_branch FOREIGN KEY (branch_id)
        REFERENCES branches (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_tours_sales_manager_user FOREIGN KEY (sales_manager_user_id)
        REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_tours_outcome FOREIGN KEY (outcome_id)
        REFERENCES tour_outcomes (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_tours_lead_id ON tours (lead_id);
CREATE INDEX idx_tours_branch_id ON tours (branch_id);
CREATE INDEX idx_tours_sales_manager_user_id ON tours (sales_manager_user_id);
CREATE INDEX idx_tours_outcome_id ON tours (outcome_id);
CREATE INDEX idx_tours_scheduled_at ON tours (scheduled_at);

CREATE TABLE lead_duplicates (
    id UUID NOT NULL,
    lead_id UUID NOT NULL,
    duplicate_of_lead_id UUID NOT NULL,
    detected_by_user_id UUID,
    detected_at TIMESTAMPTZ NOT NULL,
    reason TEXT,
    resolved_at TIMESTAMPTZ,
    resolved_by_user_id UUID,
    CONSTRAINT pk_lead_duplicates PRIMARY KEY (id),
    CONSTRAINT uk_lead_duplicates_directed_pair UNIQUE (lead_id, duplicate_of_lead_id),
    CONSTRAINT ck_lead_duplicates_not_self CHECK (lead_id <> duplicate_of_lead_id),
    CONSTRAINT fk_lead_duplicates_lead FOREIGN KEY (lead_id)
        REFERENCES leads (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_lead_duplicates_duplicate_of_lead FOREIGN KEY (duplicate_of_lead_id)
        REFERENCES leads (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_lead_duplicates_detected_by_user FOREIGN KEY (detected_by_user_id)
        REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_lead_duplicates_resolved_by_user FOREIGN KEY (resolved_by_user_id)
        REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_lead_duplicates_duplicate_of_lead_id
    ON lead_duplicates (duplicate_of_lead_id);
CREATE INDEX idx_lead_duplicates_detected_by_user_id
    ON lead_duplicates (detected_by_user_id);
CREATE INDEX idx_lead_duplicates_resolved_by_user_id
    ON lead_duplicates (resolved_by_user_id);
