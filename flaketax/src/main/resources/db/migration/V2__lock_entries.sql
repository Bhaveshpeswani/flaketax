CREATE FUNCTION refuse_entry_change() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Ledger entries cannot be edited or deleted';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER entries_are_append_only
BEFORE UPDATE OR DELETE ON entries
FOR EACH ROW EXECUTE FUNCTION refuse_entry_change();
