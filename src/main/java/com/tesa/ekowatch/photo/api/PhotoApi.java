package com.tesa.ekowatch.photo.api;

import java.util.UUID;

public interface PhotoApi {
    /** Private trusted-principal actorId. Readable by owner or as attached report evidence. */
    PhotoView getPhoto(UUID actorId, UUID photoId);

    /** Sanitises and stores a resident-uploaded JPEG or PNG photo (max 5 MiB), stripping EXIF/GPS/XMP metadata. */
    PhotoView uploadPhoto(UUID actorId, String contentType, byte[] rawBytes);
}
