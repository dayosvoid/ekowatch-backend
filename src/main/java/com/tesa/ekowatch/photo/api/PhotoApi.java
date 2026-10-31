package com.tesa.ekowatch.photo.api;
import java.util.UUID;
public interface PhotoApi {
    /** Private trusted-principal actorId. Readable by owner or as attached report evidence. */
    PhotoView getPhoto(UUID actorId, UUID photoId);
}
