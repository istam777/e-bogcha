INSERT INTO languages (
    id,
    code,
    name,
    is_active,
    sort_order
)
VALUES
    ('fb5c59da-9ce9-4b1c-8093-708df6dff228', 'UZ', 'Uzbek', TRUE, 10),
    ('a5743740-acbf-4356-b079-b6b9fe75f517', 'RU', 'Russian', TRUE, 20);

INSERT INTO gender_types (
    id,
    code,
    name,
    is_active
)
VALUES
    ('82cc170a-af63-4aff-8777-b59b86fd79a4', 'MALE', 'Male', TRUE),
    ('5433e6c3-5497-4d02-9390-1a0617a0cba8', 'FEMALE', 'Female', TRUE);

INSERT INTO relationship_types (
    id,
    code,
    name,
    is_active
)
VALUES
    ('0021c600-8837-4088-91d6-a8d664c4101f', 'FATHER', 'Father', TRUE),
    ('de3ccd45-2fa6-4edc-bfde-bba909cd9d82', 'MOTHER', 'Mother', TRUE),
    ('48dba5fe-77b8-4df7-a691-3a6b77614ed8', 'GUARDIAN', 'Guardian', TRUE);

INSERT INTO document_types (
    id,
    code,
    name,
    applies_to,
    is_active
)
VALUES
    ('6c75f6d0-6ec3-4092-b859-94ee08425967', 'BIRTH_CERTIFICATE', 'Birth Certificate', 'CHILD', TRUE),
    ('a77a89b3-371f-4caf-bdcb-6d502bc3b720', 'PASSPORT', 'Passport', 'PERSON', TRUE),
    ('dd214a9e-daaa-42a6-887f-a2f107cf3e2f', 'ID_CARD', 'ID Card', 'PERSON', TRUE),
    ('8905ff1c-b3e2-406b-85bc-eed1e27dad02', 'PINFL', 'PINFL', 'PERSON', TRUE),
    ('7642acca-0b62-4a06-b9be-dcc573391ba5', 'MEDICAL_CERTIFICATE', 'Medical Certificate', 'CHILD', TRUE),
    ('804ce20a-6653-4ac5-8e4a-86f8bbcc544d', 'PHOTO', 'Photo', 'PERSON', TRUE);

INSERT INTO document_verification_statuses (
    id,
    code,
    name,
    is_final,
    is_active
)
VALUES
    ('0c412202-9976-49d0-96e5-c010c5caf731', 'PENDING', 'Pending', FALSE, TRUE),
    ('40e8acf6-186a-4bc5-b050-0ab076032f20', 'VERIFIED', 'Verified', TRUE, TRUE),
    ('90a8cec4-b402-466d-a6d5-4474517d9312', 'REJECTED', 'Rejected', TRUE, TRUE);
