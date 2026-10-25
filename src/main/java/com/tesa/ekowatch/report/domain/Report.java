package com.tesa.ekowatch.report.domain;

import jakarta.persistence.*;

import java.util.UUID;
import java.time.*;

@Entity
@Table(name = "reports")
public class Report {
    protected Report() {
    }

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;
    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;
    @Column(name = "ward_id", nullable = false)
    private UUID wardId;
    @Column(name = "street_id", nullable = false)
    private UUID streetId;
    @Column(name = "report_type", nullable = false)
    private String reportType;
    @Column(name = "description", nullable = false)
    private String description;
    @Column(name = "status", nullable = false)
    private String status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
