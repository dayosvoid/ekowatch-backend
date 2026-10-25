package com.tesa.ekowatch.collection.domain;

import jakarta.persistence.*;

import java.util.UUID;
import java.time.*;

@Entity
@Table(name = "collection_schedules")
public class CollectionSchedule {
    protected CollectionSchedule() {
    }

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;
    @Column(name = "street_id", nullable = false)
    private UUID streetId;
    @Column(name = "scheduled_day", nullable = false)
    private Short scheduledDay;
    @Column(name = "active", nullable = false)
    private Boolean active;
}
