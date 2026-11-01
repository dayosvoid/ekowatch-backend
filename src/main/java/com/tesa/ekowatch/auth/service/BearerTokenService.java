package com.tesa.ekowatch.auth.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
public class BearerTokenService {

    private static final String HMAC_ALG = "HmacSHA256";
    private final byte[] secret;
    private final long sessionTtlHours;
    private final Clock clock;

    @Autowired
    public BearerTokenService(
            @Value("${ekowatch.auth.token-secret:ekowatch-local-dev-secret-key-at-least-32-bytes}") String secret,
            @Value("${ekowatch.auth.session-ttl-hours:8}") long sessionTtlHours,
            Clock clock) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.sessionTtlHours = sessionTtlHours <= 0 ? 8 : sessionTtlHours;
        this.clock = clock;
    }

    public IssuedToken issueToken(UUID userId, String email, String role) {
        Instant expiresAt = clock.instant()
                .truncatedTo(ChronoUnit.SECONDS)
                .plus(Duration.ofHours(sessionTtlHours));
        String payload = userId + "|" + email + "|" + role + "|" + expiresAt.getEpochSecond();
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signature = sign(encodedPayload);
        return new IssuedToken(encodedPayload + "." + signature, expiresAt);
    }

    public Optional<TokenClaims> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot != token.lastIndexOf('.') || dot == token.length() - 1) {
            return Optional.empty();
        }
        String encodedPayload = token.substring(0, dot);
        String providedSignature = token.substring(dot + 1);
        String expectedSignature = sign(encodedPayload);
        if (!MessageDigest.isEqual(
                providedSignature.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8))) {
            return Optional.empty();
        }
        try {
            String payload = new String(
                    Base64.getUrlDecoder().decode(encodedPayload),
                    StandardCharsets.UTF_8
            );
            String[] parts = payload.split("\\|", 4);
            if (parts.length != 4) {
                return Optional.empty();
            }
            UUID userId = UUID.fromString(parts[0]);
            String email = parts[1];
            String role = parts[2];
            Instant expiresAt = Instant.ofEpochSecond(Long.parseLong(parts[3]));
            if (!clock.instant().isBefore(expiresAt)) {
                return Optional.empty();
            }
            return Optional.of(new TokenClaims(userId, email, role, expiresAt));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(secret, HMAC_ALG));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign token", ex);
        }
    }

    public record IssuedToken(String accessToken, Instant expiresAt) {
    }

    public record TokenClaims(UUID userId, String email, String role, Instant expiresAt) {
    }
}
