-- -----------------------------------------------------------------------------
-- Aligns the schema with accounting vocabulary.
--
--   transactions.from_id / to_id  →  from_account_id / to_account_id
--   operations                    →  ledger_entries
--
-- from_id said nothing about what the id pointed at; both columns hold an
-- accounts.id. "operation" is a generic software word — the rows are ledger
-- entries, one debit and one credit per movement, and naming them so removes
-- the translation step between the code and an accountant's reading of it.
--
-- Every statement below is a catalog-only rename: no table rewrite, no data
-- movement, no index rebuild. Cost is independent of table size. Each takes a
-- brief ACCESS EXCLUSIVE lock, held for the duration of the catalog update.
--
-- The rename is NOT backward compatible: an application version mapping the old
-- names fails the moment this runs. That is safe here because Flyway migrates at
-- application startup, so the previous version is already stopped — there is no
-- window where both are live. It would NOT be safe under a rolling deployment;
-- that case needs the expand/contract dance (add, dual-write, backfill, drop).
--
-- Renaming a table does not rename its indexes or constraints — Postgres keeps
-- them attached under their old names. They are renamed explicitly below, or the
-- schema ends up with idx_operations_* sitting on a table called ledger_entries.
-- -----------------------------------------------------------------------------

-- ── transactions: say what the id refers to ──────────────────────────────────

ALTER TABLE transactions RENAME COLUMN from_id TO from_account_id;
ALTER TABLE transactions RENAME COLUMN to_id   TO to_account_id;

ALTER INDEX idx_transactions_from_id RENAME TO idx_transactions_from_account_id;
ALTER INDEX idx_transactions_to_id   RENAME TO idx_transactions_to_account_id;

-- ── operations → ledger_entries ──────────────────────────────────────────────

ALTER TABLE operations RENAME TO ledger_entries;

-- RENAME CONSTRAINT on a primary key renames the backing index with it.
ALTER TABLE ledger_entries RENAME CONSTRAINT pk_operations TO pk_ledger_entries;

ALTER INDEX idx_operations_transaction_id RENAME TO idx_ledger_entries_transaction_id;
ALTER INDEX idx_operations_account_id     RENAME TO idx_ledger_entries_account_id;
ALTER INDEX idx_operations_type           RENAME TO idx_ledger_entries_type;
