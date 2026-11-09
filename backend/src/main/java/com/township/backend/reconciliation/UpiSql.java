package com.township.backend.reconciliation;

final class UpiSql {

    private UpiSql() {}

    static final String LOAD = """
            SELECT flat_id, upi_id FROM flat_upi_id
            WHERE CAST(:f AS BIGINT) IS NULL OR flat_id = CAST(:f AS BIGINT)
            ORDER BY flat_id, upi_id""";

    static final String LEARN = """
            INSERT INTO flat_upi_id (flat_id, upi_id, source) VALUES (:f, :u, 'LEARNED')
            ON CONFLICT (flat_id, upi_id) DO NOTHING""";
}