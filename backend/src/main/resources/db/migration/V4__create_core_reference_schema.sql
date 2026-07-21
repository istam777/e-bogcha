CREATE TABLE languages (
    id UUID NOT NULL,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT pk_languages PRIMARY KEY (id),
    CONSTRAINT uk_languages_code UNIQUE (code)
);

CREATE TABLE nationalities (
    id UUID NOT NULL,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(100) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_nationalities PRIMARY KEY (id),
    CONSTRAINT uk_nationalities_code UNIQUE (code)
);

CREATE TABLE gender_types (
    id UUID NOT NULL,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(100) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_gender_types PRIMARY KEY (id),
    CONSTRAINT uk_gender_types_code UNIQUE (code)
);

CREATE TABLE relationship_types (
    id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_relationship_types PRIMARY KEY (id),
    CONSTRAINT uk_relationship_types_code UNIQUE (code)
);

CREATE TABLE document_types (
    id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    applies_to VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_document_types PRIMARY KEY (id),
    CONSTRAINT uk_document_types_code UNIQUE (code)
);

CREATE TABLE document_verification_statuses (
    id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    is_final BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_document_verification_statuses PRIMARY KEY (id),
    CONSTRAINT uk_document_verification_statuses_code UNIQUE (code)
);
