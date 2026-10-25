package com.tesa.ekowatch.collection.domain;

import jakarta.persistence.*;

import java.util.UUID;
import java.time.*;

@Entity
@Table(name = "pickups")
public class Pickup {
    protected Pickup() {
    }

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;
    @Column(name = "street_id", nullable = false)
    private UUID streetId;
    @Column(name = "pickup_date", nullable = false)
    private LocalDate pickupDate;
    @Column(name = "status", nullable = false)
    private String status;
    @Column(name = "recorded_by", nullable = false)
    private UUID recordedBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
