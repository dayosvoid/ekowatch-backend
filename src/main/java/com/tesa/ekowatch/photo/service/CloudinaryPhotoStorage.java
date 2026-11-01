package com.tesa.ekowatch.photo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tesa.ekowatch.shared.RuleViolation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class CloudinaryPhotoStorage {

    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final String folder;
    private final Clock clock;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public CloudinaryPhotoStorage(
            @Value("${ekowatch.cloudinary.url:}") String cloudinaryUrl,
            @Value("${ekowatch.cloudinary.cloud-name:}") String cloudName,
            @Value("${ekowatch.cloudinary.api-key:}") String apiKey,
            @Value("${ekowatch.cloudinary.api-secret:}") String apiSecret,
            @Value("${ekowatch.cloudinary.folder:ekowatch/photos}") String folder,
            Clock clock) {
        this(cloudinaryUrl, cloudName, apiKey, apiSecret, folder, clock,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                new ObjectMapper());
    }

    public CloudinaryPhotoStorage(
            String cloudinaryUrl,
            String cloudName,
            String apiKey,
            String apiSecret,
            String folder,
            Clock clock,
            HttpClient httpClient,
            ObjectMapper objectMapper) {

        ParsedCredentials parsed = parseCredentials(cloudinaryUrl, cloudName, apiKey, apiSecret);
        this.cloudName = parsed.cloudName();
        this.apiKey = parsed.apiKey();
        this.apiSecret = parsed.apiSecret();
        this.folder = (folder == null || folder.isBlank()) ? "ekowatch/photos" : folder.trim();
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return cloudName != null && !cloudName.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && apiSecret != null && !apiSecret.isBlank();
    }

    public String uploadSanitized(UUID photoId, PhotoSanitizer.SanitizedPhoto sanitized) {
        if (!isConfigured()) {
            return folder + "/" + photoId + sanitized.extension();
        }
        long timestamp = clock.instant().getEpochSecond();
        String publicId = photoId.toString();
        String signature = signUploadParams(folder, publicId, timestamp, apiSecret);
        String dataUri = "data:" + sanitized.contentType() + ";base64,"
                + Base64.getEncoder().encodeToString(sanitized.bytes());

        String formBody = "file=" + urlEncode(dataUri)
                + "&api_key=" + urlEncode(apiKey)
                + "&timestamp=" + timestamp
                + "&folder=" + urlEncode(folder)
                + "&public_id=" + urlEncode(publicId)
                + "&signature=" + urlEncode(signature);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.cloudinary.com/v1_1/" + cloudName + "/image/upload"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuleViolation("INTERNAL_ERROR");
            }
            JsonNode json = objectMapper.readTree(response.body());
            String returnedPublicId = json.path("public_id").asText(folder + "/" + publicId);
            long version = json.path("version").asLong(0L);
            String format = json.path("format").asText(
                    sanitized.extension().startsWith(".")
                            ? sanitized.extension().substring(1)
                            : sanitized.extension()
            );
            if (version > 0) {
                return "v" + version + "/" + returnedPublicId + "." + format;
            }
            return returnedPublicId + "." + format;
        } catch (RuleViolation rv) {
            throw rv;
        } catch (Exception ex) {
            throw new RuleViolation("INTERNAL_ERROR");
        }
    }

    public String resolveUrl(String storageKey) {
        if (!isConfigured() || storageKey == null || storageKey.isBlank()) {
            return null;
        }
        String normalizedKey = storageKey.startsWith("/") ? storageKey.substring(1) : storageKey;
        return "https://res.cloudinary.com/" + cloudName + "/image/upload/" + normalizedKey;
    }

    public static String signUploadParams(String folder, String publicId, long timestamp, String apiSecret) {
        String toSign = "folder=" + folder + "&public_id=" + publicId + "&timestamp=" + timestamp + apiSecret;
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1.digest(toSign.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-1 unavailable", ex);
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static ParsedCredentials parseCredentials(
            String cloudinaryUrl,
            String cloudName,
            String apiKey,
            String apiSecret) {

        if (cloudinaryUrl != null && !cloudinaryUrl.isBlank() && cloudinaryUrl.startsWith("cloudinary://")) {
            try {
                URI uri = URI.create(cloudinaryUrl.trim());
                String userInfo = uri.getUserInfo();
                String host = uri.getHost();
                if (userInfo != null && userInfo.contains(":") && host != null && !host.isBlank()) {
                    int colon = userInfo.indexOf(':');
                    String parsedKey = userInfo.substring(0, colon);
                    String parsedSecret = userInfo.substring(colon + 1);
                    return new ParsedCredentials(host, parsedKey, parsedSecret);
                }
            } catch (RuntimeException ignored) {
                // Fall back to explicit properties if URL is malformed
            }
        }
        return new ParsedCredentials(
                cloudName == null ? "" : cloudName.trim(),
                apiKey == null ? "" : apiKey.trim(),
                apiSecret == null ? "" : apiSecret.trim()
        );
    }

    private record ParsedCredentials(String cloudName, String apiKey, String apiSecret) {
    }
}
