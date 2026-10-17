GRANT USAGE ON SCHEMA public TO "${app_role}";
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO "${app_role}";
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO "${app_role}";

REVOKE ALL ON "${flyway:table}" FROM "${app_role}";
REVOKE UPDATE, DELETE ON ledger_entries, trx_state_historics FROM "${app_role}";
