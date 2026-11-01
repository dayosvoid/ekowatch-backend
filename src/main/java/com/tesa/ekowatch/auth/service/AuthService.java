package com.tesa.ekowatch.auth.service;

import com.tesa.ekowatch.auth.api.AuthApi;
import com.tesa.ekowatch.auth.api.CreateSessionRequest;
import com.tesa.ekowatch.auth.api.SessionView;
import com.tesa.ekowatch.shared.OperationLog;
import com.tesa.ekowatch.shared.RuleViolation;
import com.tesa.ekowatch.user.api.CurrentUserView;
import com.tesa.ekowatch.user.api.UserApi;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AuthService implements AuthApi {

    private final JdbcTemplate db;
    private final PasswordEncoder passwordEncoder;
    private final BearerTokenService tokenService;
    private final UserApi userApi;

    public AuthService(
            JdbcTemplate db,
            PasswordEncoder passwordEncoder,
            BearerTokenService tokenService,
            UserApi userApi) {
        this.db = db;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.userApi = userApi;
    }

    @Override
    public SessionView createSession(CreateSessionRequest request) {
        return OperationLog.record("createSession", () -> {
            if (request == null
                    || request.email() == null || request.email().isBlank()
                    || request.password() == null || request.password().isBlank()) {
                throw new RuleViolation("VALIDATION_FAILED");
            }

            String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
            List<AuthRow> rows = db.query(
                    """
                    SELECT id, email, password_hash, role
                    FROM users
                    WHERE lower(email) = ?
                    """,
                    (rs, rowNum) -> new AuthRow(
                            rs.getObject("id", UUID.class),
                            rs.getString("email"),
                            rs.getString("password_hash"),
                            rs.getString("role")
                    ),
                    normalizedEmail
            );

            if (rows.isEmpty()) {
                throw new RuleViolation("UNAUTHENTICATED");
            }

            AuthRow row = rows.getFirst();
            if (row.passwordHash() == null
                    || row.passwordHash().isBlank()
                    || row.passwordHash().startsWith("!DEMO_LOGIN_DISABLED")) {
                throw new RuleViolation("UNAUTHENTICATED");
            }

            boolean matches;
            try {
                matches = passwordEncoder.matches(request.password(), row.passwordHash());
            } catch (RuntimeException ex) {
                matches = false;
            }
            if (!matches) {
                throw new RuleViolation("UNAUTHENTICATED");
            }

            BearerTokenService.IssuedToken issued = tokenService.issueToken(
                    row.id(),
                    row.email(),
                    row.role()
            );
            CurrentUserView currentUser = userApi.getCurrentUser(row.id());
            return new SessionView(
                    issued.accessToken(),
                    "Bearer",
                    issued.expiresAt(),
                    currentUser
            );
        });
    }

    private record AuthRow(UUID id, String email, String passwordHash, String role) {
    }
}
