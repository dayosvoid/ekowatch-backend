package com.tesa.ekowatch.confirmation.domain;

import jakarta.persistence.*;

import java.util.UUID;
import java.time.*;

@Entity
@Table(name = "confirmations")
public class Confirmation {
    protected Confirmation() {
    }

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;
    @Column(name = "report_id", nullable = false)
    private UUID reportId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
