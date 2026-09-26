-- Legacy Hibernate-created schemas used an enum missing reservation/refund values.
-- Preserve every existing row while aligning with the Flyway VARCHAR contract.
ALTER TABLE project_credit_ledger
    MODIFY COLUMN entry_type VARCHAR(32) COLLATE utf8mb4_unicode_ci NOT NULL;
