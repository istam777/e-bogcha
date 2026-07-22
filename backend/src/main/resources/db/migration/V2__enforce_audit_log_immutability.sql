CREATE FUNCTION reject_audit_log_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs is append-only; UPDATE and DELETE are prohibited'
        USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER trg_audit_logs_reject_mutation
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW
    EXECUTE FUNCTION reject_audit_log_mutation();
