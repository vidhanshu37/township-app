package com.township.backend.complaints;

final class PhotoSql {

    private PhotoSql() {
    }

    static final String COMPLAINT_FLAT = "SELECT flat_id FROM complaint WHERE id = :id";

    static final String LOCK_COMPLAINT = "SELECT flat_id FROM complaint WHERE id = :id FOR UPDATE";

    static final String COUNT = "SELECT count(*) FROM complaint_photo WHERE complaint_id = :id";

    static final String INSERT = """
            INSERT INTO complaint_photo (complaint_id, uploaded_by, content_type, data)
            VALUES (:id, :u, :t, :d) RETURNING id""";

    static final String LIST = """
            SELECT id, content_type, octet_length(data) AS size_bytes, created_at
            FROM complaint_photo WHERE complaint_id = :id ORDER BY id""";

    static final String GET = "SELECT content_type, data FROM complaint_photo WHERE id = :p AND complaint_id = :id";
}