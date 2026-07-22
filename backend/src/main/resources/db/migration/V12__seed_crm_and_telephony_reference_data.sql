INSERT INTO lead_task_statuses (id, code, name, is_closed, is_active)
VALUES
    ('e17553cb-84e0-4f6b-9d89-daf1171800c6', 'OPEN', 'Open', FALSE, TRUE),
    ('a711f203-49d7-43fe-ae64-6b3b2b662702', 'IN_PROGRESS', 'In Progress', FALSE, TRUE),
    ('396c77c7-c5d3-4e56-9f9a-b64019872b91', 'COMPLETED', 'Completed', TRUE, TRUE),
    ('e8d7cdc6-6e26-4d7b-b995-f80a46d148eb', 'CANCELLED', 'Cancelled', TRUE, TRUE);

INSERT INTO lead_activity_types (id, code, name, is_active)
VALUES
    ('72af8200-7d11-448b-a40c-fd22f64dc69c', 'CALL', 'Call', TRUE),
    ('de43e843-46cf-43d8-9e1e-0930bd3f2b79', 'NOTE', 'Note', TRUE),
    ('7a1c856c-c25a-461d-8b21-aae646911d51', 'STATUS_CHANGE', 'Status Change', TRUE),
    ('80455b96-6b6f-4ae8-a6d8-414c5773cf55', 'ASSIGNMENT', 'Assignment', TRUE),
    ('9928bbba-0aba-4205-b9de-6fef84ff5865', 'TOUR', 'Tour', TRUE),
    ('5a8ad0a2-8683-489f-b71f-788887c65744', 'SYSTEM', 'System', TRUE);

INSERT INTO call_directions (id, code, name)
VALUES
    ('12cdc1a3-4e6a-4f6d-a825-ff8bc5fa9391', 'INBOUND', 'Inbound'),
    ('440c2104-1afc-4d3d-bf60-f25f3609e8ba', 'OUTBOUND', 'Outbound');

INSERT INTO call_dispositions (id, code, name, is_missed)
VALUES
    ('6553de3a-4c44-485f-93b3-fb767866cb4b', 'ANSWERED', 'Answered', FALSE),
    ('5f73f656-69fe-4076-a922-e66f0a09be4a', 'MISSED', 'Missed', TRUE),
    ('7160c287-62ae-4522-823f-74c37e3fb454', 'BUSY', 'Busy', FALSE),
    ('472be4fc-cf50-4dc2-98ea-e1d51e6d141b', 'REJECTED', 'Rejected', FALSE),
    ('c196bc93-f295-4edf-80e8-5a6a08e34f2e', 'FAILED', 'Failed', FALSE),
    ('e55118ed-1e56-440e-9dfb-415c96ae2533', 'NO_ANSWER', 'No Answer', FALSE);

INSERT INTO call_event_types (id, code, name)
VALUES
    ('9fbd9a57-37ff-40bb-aaab-fa184c5260bc', 'STARTED', 'Started'),
    ('eabb3ab9-78fb-4b04-9222-9443c8883f12', 'RINGING', 'Ringing'),
    ('ac5d2745-01b3-45e8-8e5b-bd55fea40889', 'ANSWERED', 'Answered'),
    ('23d85d99-98db-4ff4-8fee-96573bb36579', 'ENDED', 'Ended'),
    ('0123d4ef-f3a3-4efc-8d76-cfea8c345830', 'RECORDING_AVAILABLE', 'Recording Available');

INSERT INTO webhook_statuses (id, code, name, is_final)
VALUES
    ('182f569e-d993-4ed8-b26c-1c4d3c333bb8', 'RECEIVED', 'Received', FALSE),
    ('cbb743e8-4f1a-4776-96fe-2523ef0c4638', 'PROCESSING', 'Processing', FALSE),
    ('6ec1db81-3e04-481c-b4e1-aaffeacf58d0', 'PROCESSED', 'Processed', TRUE),
    ('cc8204f8-f7a9-4db8-8cb4-87e5d53aacd8', 'FAILED', 'Failed', TRUE),
    ('e6c3c3ae-eb91-44c2-ae6b-af1a9f67bf85', 'IGNORED', 'Ignored', TRUE);
