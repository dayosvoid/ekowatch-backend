package com.tesa.ekowatch.photo.service;
import com.tesa.ekowatch.photo.api.*;
import com.tesa.ekowatch.user.api.ActorAccess;
import com.tesa.ekowatch.shared.RuleViolation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PhotoService implements PhotoApi {
    private final JdbcTemplate db;
    private final ActorAccess actors;
    private final ObjectProvider<PhotoUrlResolver> urls;
    public PhotoService(JdbcTemplate db, ActorAccess actors, ObjectProvider<PhotoUrlResolver> urls) {
        this.db = db; this.actors = actors; this.urls = urls;
    }

    public PhotoView getPhoto(UUID actorId, UUID photoId) {
        actors.requireUser(actorId);
        if (photoId == null) throw new RuleViolation("VALIDATION_FAILED");
        return db.query("""
                select p.id,p.storage_key,p.content_type,p.size_bytes,p.created_at from photos p
                where p.id=? and (p.owner_id=? or exists(select 1 from reports r where r.photo_id=p.id))
                """, (r,n) -> {
                    PhotoUrlResolver resolver = urls.getIfAvailable();
                    return new PhotoView(r.getObject("id",UUID.class), resolver == null ? null : resolver.resolve(r.getString("storage_key")),
                            r.getString("content_type"),r.getLong("size_bytes"),r.getTimestamp("created_at").toInstant());
                }, photoId, actorId).stream().findFirst().orElseThrow(() -> new RuleViolation("RESOURCE_NOT_FOUND"));
    }
}
