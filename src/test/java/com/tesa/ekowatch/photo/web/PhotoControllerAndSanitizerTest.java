package com.tesa.ekowatch.photo.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tesa.ekowatch.photo.api.PhotoApi;
import com.tesa.ekowatch.photo.api.PhotoView;
import com.tesa.ekowatch.photo.service.CloudinaryPhotoStorage;
import com.tesa.ekowatch.photo.service.PhotoSanitizer;
import com.tesa.ekowatch.shared.ApiExceptionHandler;
import com.tesa.ekowatch.shared.RuleViolation;
import com.tesa.ekowatch.user.api.ActorAccess;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PhotoControllerAndSanitizerTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PHOTO_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");

    private static byte[] createJpegWithExifAndGps() throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 16, 16);
        g.dispose();

        ByteArrayOutputStream rawJpeg = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", rawJpeg);
        byte[] jpegBytes = rawJpeg.toByteArray();

        byte[] fakeExifGpsPayload = "Exif\0\0GPS-LAT:6.4550;GPS-LON:3.3941;http://ns.adobe.com/xap/1.0/"
                .getBytes(StandardCharsets.US_ASCII);
        int segLen = fakeExifGpsPayload.length + 2;

        ByteArrayOutputStream withApp1 = new ByteArrayOutputStream();
        withApp1.write(jpegBytes, 0, 2); // SOI FF D8
        withApp1.write(0xFF);
        withApp1.write(0xE1); // APP1 marker
        withApp1.write((segLen >> 8) & 0xFF);
        withApp1.write(segLen & 0xFF);
        withApp1.write(fakeExifGpsPayload);
        withApp1.write(jpegBytes, 2, jpegBytes.length - 2);
        return withApp1.toByteArray();
    }

    @Test
    void sanitizerStripsExifGpsAndXmpMetadataFromJpegAndEnforcesSizeAndTypeLimits() throws Exception {
        byte[] jpegWithExif = createJpegWithExifAndGps();
        assertTrue(PhotoSanitizer.containsExifOrXmpMetadata(jpegWithExif));

        PhotoSanitizer.SanitizedPhoto sanitized = PhotoSanitizer.sanitize("image/jpeg", jpegWithExif);
        assertEquals("image/jpeg", sanitized.contentType());
        assertFalse(PhotoSanitizer.containsExifOrXmpMetadata(sanitized.bytes()));
        assertTrue(sanitized.bytes().length > 0);

        byte[] oversized = new byte[(int) PhotoSanitizer.MAX_PHOTO_BYTES + 1];
        assertEquals(
                "PHOTO_TOO_LARGE",
                assertThrows(RuleViolation.class, () -> PhotoSanitizer.sanitize("image/jpeg", oversized)).code()
        );

        byte[] textDisguisedAsJpeg = "not-an-image-at-all".getBytes(StandardCharsets.UTF_8);
        assertEquals(
                "UNSUPPORTED_PHOTO_TYPE",
                assertThrows(RuleViolation.class, () -> PhotoSanitizer.sanitize("image/jpeg", textDisguisedAsJpeg)).code()
        );
    }

    @Test
    void photoControllerHandlesUploadAndGetAndMaps413And415ProblemJson() throws Exception {
        PhotoApi photos = mock(PhotoApi.class);
        ActorAccess actors = mock(ActorAccess.class);
        Authentication auth = mock(Authentication.class);
        when(actors.id(auth)).thenReturn(ACTOR_ID);

        PhotoController controller = new PhotoController(photos, actors);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        byte[] validJpeg = createJpegWithExifAndGps();
        PhotoView view = new PhotoView(
                PHOTO_ID,
                "/api/v1/photos/files/photos/" + PHOTO_ID + ".jpg",
                "image/jpeg",
                512L,
                Instant.parse("2026-10-31T12:00:00Z")
        );
        when(photos.uploadPhoto(eq(ACTOR_ID), eq("image/jpeg"), any(byte[].class))).thenReturn(view);
        when(photos.getPhoto(ACTOR_ID, PHOTO_ID)).thenReturn(view);

        MockMultipartFile file = new MockMultipartFile("file", "drain.jpg", "image/jpeg", validJpeg);
        mvc.perform(multipart("/api/v1/photos").file(file).principal(auth))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.photoId").value(PHOTO_ID.toString()))
                .andExpect(jsonPath("$.contentType").value("image/jpeg"));

        mvc.perform(get("/api/v1/photos/{photoId}", PHOTO_ID).principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoId").value(PHOTO_ID.toString()));

        when(photos.uploadPhoto(eq(ACTOR_ID), eq("text/plain"), any(byte[].class)))
                .thenThrow(new RuleViolation("UNSUPPORTED_PHOTO_TYPE"));
        MockMultipartFile badType = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/v1/photos").file(badType).principal(auth))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_PHOTO_TYPE"))
                .andExpect(jsonPath("$.status").value(415));

        when(photos.uploadPhoto(eq(ACTOR_ID), eq("image/png"), any(byte[].class)))
                .thenThrow(new RuleViolation("PHOTO_TOO_LARGE"));
        MockMultipartFile hugeFile = new MockMultipartFile("file", "huge.png", "image/png", new byte[]{1, 2, 3});
        mvc.perform(multipart("/api/v1/photos").file(hugeFile).principal(auth))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("PHOTO_TOO_LARGE"))
                .andExpect(jsonPath("$.status").value(413));
    }

    @Test
    @SuppressWarnings("unchecked")
    void cloudinaryPhotoStorageUploadsSanitizedBytesAndResolvesDeliveryUrl() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> httpResponse = mock(HttpResponse.class);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("""
                {"public_id":"ekowatch/photos/%s","version":1730419200,"format":"jpg"}
                """.formatted(PHOTO_ID));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        Clock fixedClock = Clock.fixed(Instant.parse("2026-10-31T12:00:00Z"), ZoneOffset.UTC);
        CloudinaryPhotoStorage storage = new CloudinaryPhotoStorage(
                "cloudinary://123456789:my_secret_key@ekowatch-cloud",
                "",
                "",
                "",
                "ekowatch/photos",
                fixedClock,
                httpClient,
                new ObjectMapper()
        );
        assertTrue(storage.isConfigured());

        PhotoSanitizer.SanitizedPhoto sanitized = PhotoSanitizer.sanitize("image/jpeg", createJpegWithExifAndGps());
        String storageKey = storage.uploadSanitized(PHOTO_ID, sanitized);
        assertEquals("v1730419200/ekowatch/photos/" + PHOTO_ID + ".jpg", storageKey);
        assertEquals(
                "https://res.cloudinary.com/ekowatch-cloud/image/upload/v1730419200/ekowatch/photos/" + PHOTO_ID + ".jpg",
                storage.resolveUrl(storageKey)
        );
    }
}
