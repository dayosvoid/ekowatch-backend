package com.tesa.ekowatch.photo.service;

import com.tesa.ekowatch.photo.api.PhotoApi;
import com.tesa.ekowatch.photo.api.PhotoUrlResolver;
import com.tesa.ekowatch.photo.api.PhotoView;
import com.tesa.ekowatch.shared.OperationLog;
import com.tesa.ekowatch.shared.RuleViolation;
import com.tesa.ekowatch.user.api.ActorAccess;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Transactional(readOnly = true)
public class PhotoService implements PhotoApi {

    private final JdbcTemplate db;
    private final ActorAccess actors;
    private final ObjectProvider<PhotoUrlResolver> urls;
    private final Clock clock;
    private final CloudinaryPhotoStorage cloudinaryStorage;
    private final Map<String, byte[]> sanitizedStore = new ConcurrentHashMap<>();

    public PhotoService(JdbcTemplate db, ActorAccess actors, ObjectProvider<PhotoUrlResolver> urls) {
        this(db, actors, urls, Clock.systemUTC(), null);
    }

    @Autowired
    public PhotoService(
            JdbcTemplate db,
            ActorAccess actors,
            ObjectProvider<PhotoUrlResolver> urls,
            Clock clock,
            CloudinaryPhotoStorage cloudinaryStorage) {
        this.db = db;
        this.actors = actors;
        this.urls = urls;
        this.clock = clock;
        this.cloudinaryStorage = cloudinaryStorage;
    }

    @Override
    public PhotoView getPhoto(UUID actorId, UUID photoId) {
        actors.requireUser(actorId);
        if (photoId == null) throw new RuleViolation("VALIDATION_FAILED");
        return db.query("""
                select p.id,p.storage_key,p.content_type,p.size_bytes,p.created_at from photos p
                where p.id=? and (p.owner_id=? or exists(select 1 from reports r where r.photo_id=p.id))
                """, (r, n) -> {
                    String storageKey = r.getString("storage_key");
                    return new PhotoView(
                            r.getObject("id", UUID.class),
                            resolvePhotoUrl(storageKey),
                            r.getString("content_type"),
                            r.getLong("size_bytes"),
                            r.getTimestamp("created_at").toInstant()
                    );
                }, photoId, actorId).stream().findFirst().orElseThrow(() -> new RuleViolation("RESOURCE_NOT_FOUND"));
    }

    @Override
    @Transactional
    public PhotoView uploadPhoto(UUID actorId, String contentType, byte[] rawBytes) {
        return OperationLog.record("uploadPhoto", () -> {
            actors.requireRole(actorId, "RESIDENT");
            PhotoSanitizer.SanitizedPhoto sanitized = PhotoSanitizer.sanitize(contentType, rawBytes);
            UUID photoId = UUID.randomUUID();
            String storageKey = cloudinaryStorage != null && cloudinaryStorage.isConfigured()
                    ? cloudinaryStorage.uploadSanitized(photoId, sanitized)
                    : "photos/" + photoId + sanitized.extension();
            Instant createdAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
            long sizeBytes = sanitized.bytes().length;

            db.update(
                    """
                    insert into photos(id, owner_id, storage_key, content_type, size_bytes, created_at)
                    values (?, ?, ?, ?, ?, ?)
                    """,
                    photoId,
                    actorId,
                    storageKey,
                    sanitized.contentType(),
                    sizeBytes,
                    Timestamp.from(createdAt)
            );

            sanitizedStore.put(storageKey, sanitized.bytes());
            String photoUrl = resolvePhotoUrl(storageKey);

            return new PhotoView(
                    photoId,
                    photoUrl,
                    sanitized.contentType(),
                    sizeBytes,
                    createdAt
            );
        });
    }

    private String resolvePhotoUrl(String storageKey) {
        PhotoUrlResolver resolver = urls.getIfAvailable();
        if (resolver != null) {
            return resolver.resolve(storageKey);
        }
        if (cloudinaryStorage != null && cloudinaryStorage.isConfigured()) {
            return cloudinaryStorage.resolveUrl(storageKey);
        }
        return null;
    }

    public byte[] getSanitizedBytes(String storageKey) {
        byte[] stored = sanitizedStore.get(storageKey);
        return stored == null ? null : stored.clone();
    }
}
