package com.tesa.ekowatch.user.domain;

import jakarta.persistence.*;

import java.util.UUID;
import java.time.*;

@Entity
@Table(name = "users")
public class UserAccount {
    protected UserAccount() {
    }

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;
    @Column(name = "email", nullable = false)
    private String email;
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    @Column(name = "display_name", nullable = false)
    private String displayName;
    @Column(name = "role", nullable = false)
    private String role;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
