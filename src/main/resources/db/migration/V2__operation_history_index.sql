CREATE INDEX ix_operations_account_created_id ON operations(account_id, created_at DESC, id DESC);
