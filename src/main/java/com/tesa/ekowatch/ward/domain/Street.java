package com.tesa.ekowatch.ward.domain;

import jakarta.persistence.*;

import java.util.UUID;
import java.time.*;

@Entity
@Table(name = "streets")
public class Street {
    protected Street() {
    }

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;
    @Column(name = "ward_id", nullable = false)
    private UUID wardId;
    @Column(name = "name", nullable = false)
    private String name;
    @Column(name = "latitude")
    private Double latitude;
    @Column(name = "longitude")
    private Double longitude;
}
