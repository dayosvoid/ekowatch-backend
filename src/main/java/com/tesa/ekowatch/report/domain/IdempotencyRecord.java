package com.tesa.ekowatch.report.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

/** Completed report-creation snapshots only; service persistence uses JdbcTemplate. */
@Entity
@Table(name="idempotency_records")
public class IdempotencyRecord {
    protected IdempotencyRecord() {}
    @Id @Column(nullable=false) private UUID id;
    @Column(name="user_id",nullable=false) private UUID userId;
    @Column(nullable=false,length=40) private String operation;
    @Column(name="idempotency_key",nullable=false) private UUID idempotencyKey;
    @Column(name="request_hash",nullable=false,length=64) private String requestHash;
    @Column(name="report_id",nullable=false) private UUID reportId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name="response_payload",nullable=false,columnDefinition="jsonb") private String responsePayload;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="expires_at",nullable=false) private Instant expiresAt;
}
