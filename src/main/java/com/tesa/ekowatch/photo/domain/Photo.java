package com.tesa.ekowatch.photo.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Persistence only. storageKey must reference an asset sanitised by the trusted adapter. */
@Entity
@Table(name = "photos")
public class Photo {
    protected Photo() {
    }

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;
    @Column(name = "storage_key", nullable = false, columnDefinition = "text")
    private String storageKey;
    @Column(name = "content_type", nullable = false)
    private String contentType;
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
