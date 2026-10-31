package com.tesa.ekowatch.report.domain;

import jakarta.persistence.*;
import com.tesa.ekowatch.report.api.ReportType;

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
    @Enumerated(EnumType.STRING)
    private ReportType reportType;
    @Column(name = "note", nullable = false)
    private String note;
    @Column(name = "photo_id")
    private UUID photoId;
    @Column(name = "latitude")
    private Double latitude;
    @Column(name = "longitude")
    private Double longitude;
    @Column(name = "collection_date")
    private LocalDate collectionDate;
    @Column(name = "status", nullable = false)
    private String status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
