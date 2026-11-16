EXPLAIN (ANALYZE, BUFFERS)
SELECT id, account_id, amount, currency, description, status, created_at, updated_at, version
FROM operations WHERE account_id='11111111-1111-1111-1111-111111111111'
ORDER BY created_at DESC, id DESC LIMIT 20;
-- Compare a plan without the index without changing the schema; rollback restores planner settings.
BEGIN;
SET LOCAL enable_indexscan=off;
SET LOCAL enable_bitmapscan=off;
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, account_id, amount, currency, description, status, created_at, updated_at, version
FROM operations WHERE account_id='11111111-1111-1111-1111-111111111111'
ORDER BY created_at DESC, id DESC LIMIT 20;
ROLLBACK;
