package com.tesa.ekowatch.photo.service;

import com.tesa.ekowatch.shared.RuleViolation;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class PhotoSanitizer {

    public static final long MAX_PHOTO_BYTES = 5L * 1024L * 1024L;
    private static final int MAX_DIMENSION = 8192;

    private PhotoSanitizer() {
    }

    public static SanitizedPhoto sanitize(String declaredContentType, byte[] rawBytes) {
        if (rawBytes == null || rawBytes.length == 0) {
            throw new RuleViolation("VALIDATION_FAILED");
        }
        if (rawBytes.length > MAX_PHOTO_BYTES) {
            throw new RuleViolation("PHOTO_TOO_LARGE");
        }

        String detectedMime = detectMimeFromMagicBytes(rawBytes);
        if (detectedMime == null) {
            throw new RuleViolation("UNSUPPORTED_PHOTO_TYPE");
        }

        if (declaredContentType != null && !declaredContentType.isBlank()) {
            String normalized = declaredContentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
            if (!normalized.equals("application/octet-stream")) {
                boolean jpegMatch = detectedMime.equals("image/jpeg")
                        && (normalized.equals("image/jpeg") || normalized.equals("image/jpg"));
                boolean pngMatch = detectedMime.equals("image/png")
                        && normalized.equals("image/png");
                if (!jpegMatch && !pngMatch) {
                    throw new RuleViolation("UNSUPPORTED_PHOTO_TYPE");
                }
            }
        }

        BufferedImage decoded;
        try {
            decoded = ImageIO.read(new ByteArrayInputStream(rawBytes));
        } catch (IOException ex) {
            throw new RuleViolation("VALIDATION_FAILED");
        }

        if (decoded == null
                || decoded.getWidth() <= 0
                || decoded.getHeight() <= 0
                || decoded.getWidth() > MAX_DIMENSION
                || decoded.getHeight() > MAX_DIMENSION) {
            throw new RuleViolation("VALIDATION_FAILED");
        }

        boolean isJpeg = detectedMime.equals("image/jpeg");
        int imageType = isJpeg ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        BufferedImage cleanImage = new BufferedImage(decoded.getWidth(), decoded.getHeight(), imageType);
        Graphics2D graphics = cleanImage.createGraphics();
        try {
            if (isJpeg) {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, decoded.getWidth(), decoded.getHeight());
            }
            graphics.drawImage(decoded, 0, 0, null);
        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            boolean written = ImageIO.write(cleanImage, isJpeg ? "jpg" : "png", out);
            if (!written) {
                throw new RuleViolation("VALIDATION_FAILED");
            }
        } catch (IOException ex) {
            throw new RuleViolation("VALIDATION_FAILED");
        }

        byte[] sanitizedBytes = out.toByteArray();
        if (sanitizedBytes.length == 0 || containsExifOrXmpMetadata(sanitizedBytes)) {
            throw new RuleViolation("INTERNAL_ERROR");
        }
        if (sanitizedBytes.length > MAX_PHOTO_BYTES) {
            throw new RuleViolation("PHOTO_TOO_LARGE");
        }

        String extension = isJpeg ? ".jpg" : ".png";
        return new SanitizedPhoto(detectedMime, extension, sanitizedBytes);
    }

    public static String detectMimeFromMagicBytes(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47
                && bytes[4] == 0x0D
                && bytes[5] == 0x0A
                && bytes[6] == 0x1A
                && bytes[7] == 0x0A) {
            return "image/png";
        }
        return null;
    }

    public static boolean containsExifOrXmpMetadata(byte[] bytes) {
        if (bytes == null || bytes.length < 6) {
            return false;
        }
        byte[] exifHeader = "Exif\0\0".getBytes(StandardCharsets.US_ASCII);
        byte[] xmpHeader = "http://ns.adobe.com/xap/1.0/".getBytes(StandardCharsets.US_ASCII);
        byte[] pngExifChunk = "eXIf".getBytes(StandardCharsets.US_ASCII);
        return indexOf(bytes, exifHeader) >= 0
                || indexOf(bytes, xmpHeader) >= 0
                || indexOf(bytes, pngExifChunk) >= 0;
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    public record SanitizedPhoto(String contentType, String extension, byte[] bytes) {
    }
}
