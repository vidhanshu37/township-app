package com.township.backend.complaints;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

@RestController
public class PhotoController {

    private final PhotoService photos;

    public PhotoController(PhotoService photos) {
        this.photos = photos;
    }

    @PostMapping(value = "/api/resident/complaints/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PhotoService.PhotoInfo upload(@PathVariable long id, @RequestParam("file") MultipartFile file,
                                         @AuthenticationPrincipal Jwt jwt) {
        try {
            return photos.add(id, flatId(jwt), Long.parseLong(jwt.getSubject()), file.getBytes());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the uploaded photo");
        }
    }

    @GetMapping("/api/resident/complaints/{id}/photos")
    public List<PhotoService.PhotoInfo> mine(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return photos.list(id, flatId(jwt));
    }

    @GetMapping("/api/resident/complaints/{id}/photos/{photoId}")
    public ResponseEntity<byte[]> mineImage(@PathVariable long id, @PathVariable long photoId,
                                            @AuthenticationPrincipal Jwt jwt) {
        return image(photos.get(id, photoId, flatId(jwt)));
    }

    @GetMapping("/api/helpdesk/complaints/{id}/photos")
    public List<PhotoService.PhotoInfo> forStaff(@PathVariable long id) {
        return photos.list(id, null);
    }

    @GetMapping("/api/helpdesk/complaints/{id}/photos/{photoId}")
    public ResponseEntity<byte[]> staffImage(@PathVariable long id, @PathVariable long photoId) {
        return image(photos.get(id, photoId, null));
    }

    private static ResponseEntity<byte[]> image(PhotoService.Photo p) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(p.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .body(p.data());
    }

    private static long flatId(Jwt jwt) {
        return ((Number) jwt.getClaim("flat_id")).longValue();
    }
}