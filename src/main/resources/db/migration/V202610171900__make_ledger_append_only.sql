-- Ledger entries and state historics are append-only (L-6): a correction is a new row,
-- never an edit. A soft delete is an UPDATE, so it is refused too.

CREATE FUNCTION forbid_append_only_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% on % is forbidden: the table is append-only', TG_OP, TG_TABLE_NAME
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ledger_entries_append_only
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION forbid_append_only_change();

CREATE TRIGGER trx_state_historics_append_only
    BEFORE UPDATE OR DELETE ON trx_state_historics
    FOR EACH ROW EXECUTE FUNCTION forbid_append_only_change();

ALTER TABLE ledger_entries
    ADD CONSTRAINT ck_ledger_entries_amount CHECK (amount >= 0);
