package com.tesa.ekowatch.confirmation.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="report_confirmations")
public class ReportConfirmation {
    protected ReportConfirmation() {}
    @Id @Column(name="id",nullable=false) private UUID id;
    @Column(name="report_id",nullable=false) private UUID reportId;
    @Column(name="resident_id",nullable=false) private UUID residentId;
    @Column(name="confirmed_at",nullable=false) private Instant confirmedAt;
}
