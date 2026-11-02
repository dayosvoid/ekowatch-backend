package com.tesa.ekowatch.auth.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deliberate, standalone CLI procedure for provisioning BCrypt password hashes
 * for the five approved demo accounts (demo1, demo2, demo4, demo5, demo8).
 * Never registered as a Spring bean or executed automatically on application startup.
 */
public final class DemoAccountPasswordProvisioner {

    public static final List<String> APPROVED_DEMO_EMAILS = DemoCredentialProvisioner.APPROVED_DEMO_EMAILS;

    private DemoAccountPasswordProvisioner() {
    }

    public static void main(String[] args) throws Exception {
        Set<String> flags = new HashSet<>(Arrays.asList(args));
        if (!flags.contains("--provision-approved-demo-accounts")) {
            throw new IllegalStateException(
                    "Refusing to run without explicit --provision-approved-demo-accounts flag"
            );
        }

        String activeProfiles = System.getenv().getOrDefault("SPRING_PROFILES_ACTIVE", "");
        for (String token : activeProfiles.split(",")) {
            String normalized = token.trim().toLowerCase(Locale.ROOT);
            if (normalized.equals("prod") || normalized.equals("production")) {
                throw new IllegalStateException(
                        "Refusing to provision demo accounts when prod/production profile is active"
                );
            }
        }

        String dbUrl = requireEnv("DB_URL");
        String dbUsername = requireEnv("DB_USERNAME");
        String dbPassword = requireEnv("DB_PASSWORD");
        String demoPassword = requireEnv("EKOWATCH_DEMO_PASSWORD");
        if (demoPassword.length() < 8) {
            throw new IllegalStateException("EKOWATCH_DEMO_PASSWORD must be at least 8 characters");
        }

        Class.forName("org.postgresql.Driver");
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        try (Connection connection = DriverManager.getConnection(dbUrl, dbUsername, dbPassword)) {
            connection.setAutoCommit(false);
            try {
                if (flags.contains("--seed-demo-fixture")) {
                    SingleConnectionDataSource dataSource = new SingleConnectionDataSource(connection, true);
                    new ResourceDatabasePopulator(new ClassPathResource("db/demo/demo.sql")).execute(dataSource);
                }

                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE users SET password_hash = ? WHERE email = ?")) {
                    for (String email : APPROVED_DEMO_EMAILS) {
                        String bcryptHash = encoder.encode(demoPassword);
                        update.setString(1, bcryptHash);
                        update.setString(2, email);
                        int updated = update.executeUpdate();
                        if (updated != 1) {
                            throw new IllegalStateException("Expected demo account not found: " + email);
                        }
                    }
                }

                try (PreparedStatement check = connection.prepareStatement(
                        "SELECT email, role, password_hash FROM users ORDER BY email");
                     ResultSet rs = check.executeQuery()) {
                    while (rs.next()) {
                        String email = rs.getString("email");
                        String role = rs.getString("role");
                        String hash = rs.getString("password_hash");
                        boolean approved = APPROVED_DEMO_EMAILS.contains(email);
                        boolean matches = approved && encoder.matches(demoPassword, hash);
                        boolean disabled = "!DEMO_LOGIN_DISABLED".equals(hash);
                        System.out.printf(
                                "account=%s role=%s approved=%s bcryptVerified=%s disabledMarker=%s%n",
                                email, role, approved, matches, disabled
                        );
                        if (approved && !matches) {
                            throw new IllegalStateException("BCrypt verification failed for " + email);
                        }
                        if (!approved && !disabled) {
                            throw new IllegalStateException("Unapproved demo account must remain disabled: " + email);
                        }
                    }
                }

                connection.commit();
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            }
        }
    }

    private static String requireEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + key);
        }
        return value.trim();
    }
}
