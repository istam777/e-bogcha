INSERT INTO user_statuses (id, code, name, is_active, sort_order)
VALUES
    ('a27394d3-a644-4db1-a84f-eb7a3ac5fc47', 'ACTIVE', 'ACTIVE', TRUE, 0),
    ('e199ab44-e328-4d72-84ad-807213345872', 'INACTIVE', 'INACTIVE', TRUE, 0),
    ('c41bcf72-f77b-4a26-b331-1a37b22c2166', 'LOCKED', 'LOCKED', TRUE, 0),
    ('1f46311c-31cc-4f72-a542-075fad28373a', 'SUSPENDED', 'SUSPENDED', TRUE, 0)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    is_active = EXCLUDED.is_active,
    sort_order = EXCLUDED.sort_order;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM (
            VALUES
                ('a27394d3-a644-4db1-a84f-eb7a3ac5fc47'::UUID, 'ACTIVE'),
                ('e199ab44-e328-4d72-84ad-807213345872'::UUID, 'INACTIVE'),
                ('c41bcf72-f77b-4a26-b331-1a37b22c2166'::UUID, 'LOCKED'),
                ('1f46311c-31cc-4f72-a542-075fad28373a'::UUID, 'SUSPENDED')
        ) AS expected(id, code)
        JOIN user_statuses actual USING (code)
        WHERE actual.id <> expected.id
    ) THEN
        RAISE EXCEPTION 'A released user status code is associated with an unexpected UUID'
            USING ERRCODE = '23505';
    END IF;
END;
$$;
