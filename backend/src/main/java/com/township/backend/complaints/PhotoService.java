package com.township.backend.complaints;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class PhotoService {

    public record PhotoInfo(long id, String contentType, int sizeBytes, OffsetDateTime createdAt) {}

    public record Photo(String contentType, byte[] data) {}

    static final int MAX_PHOTOS = 3;
    static final int MAX_BYTES = 2 * 1024 * 1024;

    private final JdbcClient jdbc;

    public PhotoService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public PhotoInfo add(long complaintId, long flatId, long userId, byte[] data) {
        if (data == null || data.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The photo is empty");
        }
        if (data.length > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Photo must be 2 MB or smaller");
        }
        String type = ImageSniffer.detect(data);
        if (type == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only JPEG, PNG or WebP photos are accepted");
        }
        long owner = jdbc.sql(PhotoSql.LOCK_COMPLAINT).param("id", complaintId).query(Long.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Complaint not found"));
        if (owner != flatId) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Complaint not found");
        }
        long count = jdbc.sql(PhotoSql.COUNT).param("id", complaintId).query(Long.class).single();
        if (count >= MAX_PHOTOS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A complaint can have at most 3 photos");
        }
        long photoId = jdbc.sql(PhotoSql.INSERT).param("id", complaintId).param("u", userId)
                .param("t", type).param("d", data).query(Long.class).single();
        return new PhotoInfo(photoId, type, data.length, OffsetDateTime.now());
    }

    // flatId null means staff, who may see any complaint.
    public List<PhotoInfo> list(long complaintId, Long flatId) {
        checkAccess(complaintId, flatId);
        return jdbc.sql(PhotoSql.LIST).param("id", complaintId)
                .query((rs, n) -> new PhotoInfo(rs.getLong("id"), rs.getString("content_type"),
                        rs.getInt("size_bytes"), rs.getObject("created_at", OffsetDateTime.class)))
                .list();
    }

    public Photo get(long complaintId, long photoId, Long flatId) {
        checkAccess(complaintId, flatId);
        return jdbc.sql(PhotoSql.GET).param("p", photoId).param("id", complaintId)
                .query((rs, n) -> new Photo(rs.getString("content_type"), rs.getBytes("data")))
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found"));
    }

    private void checkAccess(long complaintId, Long flatId) {
        Optional<Long> owner = jdbc.sql(PhotoSql.COMPLAINT_FLAT).param("id", complaintId).query(Long.class).optional();
        if (owner.isEmpty() || (flatId != null && !owner.get().equals(flatId))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Complaint not found");
        }
    }
}