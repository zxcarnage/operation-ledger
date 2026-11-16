-- Run after the application has applied Flyway migrations. Idempotent local fixture.
INSERT INTO operations(id,request_id,account_id,amount,currency,description,status,created_at,updated_at)
VALUES ('22222222-2222-2222-2222-222222222222','33333333-3333-3333-3333-333333333333',
'11111111-1111-1111-1111-111111111111',125.50,'EUR','Invoice INV-1042','NEW',now(),now()) ON CONFLICT DO NOTHING;
INSERT INTO status_history(operation_id,from_status,to_status,reason,changed_at)
SELECT id,NULL,'NEW','Local fixture',created_at FROM operations o
WHERE id='22222222-2222-2222-2222-222222222222'
AND NOT EXISTS (SELECT 1 FROM status_history h WHERE h.operation_id=o.id);
