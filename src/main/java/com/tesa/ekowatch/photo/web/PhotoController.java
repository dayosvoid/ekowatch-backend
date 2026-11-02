package com.tesa.ekowatch.photo.web;

import com.tesa.ekowatch.photo.api.PhotoApi;
import com.tesa.ekowatch.photo.api.PhotoView;
import com.tesa.ekowatch.shared.RuleViolation;
import com.tesa.ekowatch.user.api.ActorAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/photos")
@Tag(name = "Photos", description = "Resident photo upload with EXIF/GPS/XMP sanitisation and Cloudinary storage")
@SecurityRequirement(name = "bearerAuth")
public class PhotoController {

    private final PhotoApi photos;
    private final ActorAccess actors;

    public PhotoController(PhotoApi photos, ActorAccess actors) {
        this.photos = photos;
        this.actors = actors;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "uploadPhoto",
            summary = "Upload and sanitise a resident report photo (JPEG/PNG, max 5 MiB, returns 201 Created)",
            description = "Requires Role: `RESIDENT`. Strips all EXIF/GPS/XMP metadata before storing in Cloudinary. Returns `413 PHOTO_TOO_LARGE` if > 5 MiB, or `415 UNSUPPORTED_PHOTO_TYPE` if not JPEG/PNG."
    )
    public PhotoView uploadPhoto(
            Authentication authentication,
            @RequestPart(value = "file", required = false) MultipartFile file) {

        UUID actorId = actors.id(authentication);
        if (file == null || file.isEmpty()) {
            throw new RuleViolation("VALIDATION_FAILED");
        }
        byte[] rawBytes;
        try {
            rawBytes = file.getBytes();
        } catch (IOException ex) {
            throw new RuleViolation("VALIDATION_FAILED");
        }
        return photos.uploadPhoto(actorId, file.getContentType(), rawBytes);
    }

    @GetMapping("/{photoId}")
    @Operation(
            operationId = "getPhoto",
            summary = "Get sanitised photo metadata and resolved URL",
            description = "Requires `Authorization: Bearer <token>`. Readable by the photo owner or when attached as report evidence."
    )
    public PhotoView getPhoto(
            Authentication authentication,
            @PathVariable UUID photoId) {

        return photos.getPhoto(actors.id(authentication), photoId);
    }
}
