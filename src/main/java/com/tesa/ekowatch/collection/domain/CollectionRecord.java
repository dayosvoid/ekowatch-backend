package com.tesa.ekowatch.collection.domain;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;
@Entity
@Table(name="collections")
public class CollectionRecord {
    protected CollectionRecord() {}
    @Id @Column(name="id",nullable=false) private UUID id;
    @Column(name="street_id",nullable=false) private UUID streetId;
    @Column(name="collection_date",nullable=false) private LocalDate collectionDate;
    @Column(name="completed_by",nullable=false) private UUID completedBy;
    @Column(name="completed_at",nullable=false) private Instant completedAt;
}