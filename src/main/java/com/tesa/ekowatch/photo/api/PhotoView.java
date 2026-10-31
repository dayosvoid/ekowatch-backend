package com.tesa.ekowatch.photo.api;
import java.time.Instant;
import java.util.UUID;
public record PhotoView(UUID photoId, String photoUrl, String contentType, long sizeBytes, Instant createdAt) {}
