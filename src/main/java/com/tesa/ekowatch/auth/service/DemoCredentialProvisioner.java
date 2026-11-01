package com.tesa.ekowatch.auth.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@Profile("local-demo & !prod & !production")
@ConditionalOnProperty(name = "ekowatch.demo.password")
public class DemoCredentialProvisioner implements ApplicationRunner {

    private final JdbcTemplate db;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    public DemoCredentialProvisioner(
            JdbcTemplate db,
            PasswordEncoder passwordEncoder,
            Environment environment) {
        this.db = db;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!environment.acceptsProfiles(Profiles.of("local-demo & !prod & !production"))
                || !Boolean.TRUE.equals(environment.getProperty("ekowatch.demo.enabled", Boolean.class, false))) {
            return;
        }
        String rawPassword = environment.getProperty("ekowatch.demo.password");
        if (rawPassword == null || rawPassword.isBlank()) {
            return;
        }
        String encoded = passwordEncoder.encode(rawPassword);
        db.update(
                "UPDATE users SET password_hash = ? WHERE email LIKE '%@demo.ekowatch.invalid' AND password_hash = '!DEMO_LOGIN_DISABLED'",
                encoded
        );
    }
}
