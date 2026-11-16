-- Disposable local database only. These are synthetic API history fixtures, not business events.
INSERT INTO operations(id,request_id,account_id,amount,currency,description,status,created_at,updated_at)
SELECT md5('operation-' || n)::uuid, md5('request-' || n)::uuid,
CASE WHEN n % 20 = 0 THEN '11111111-1111-1111-1111-111111111111'::uuid ELSE md5('account-' || n % 1000)::uuid END,
10 + n % 500, 'EUR', 'Load fixture ' || n, 'COMPLETED',
'2026-01-01'::timestamptz + n * interval '1 second', '2026-01-01'::timestamptz + n * interval '1 second'
FROM generate_series(1, 200000) n ON CONFLICT DO NOTHING;
ANALYZE operations;
