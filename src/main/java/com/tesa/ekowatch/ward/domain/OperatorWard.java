package com.tesa.ekowatch.ward.domain;

import jakarta.persistence.*;

import java.util.*;

@Entity
@Table(name = "operator_wards")
@IdClass(OperatorWard.Key.class)
public class OperatorWard {
    protected OperatorWard() {
    }

    @Id
    @Column(name = "operator_id", nullable = false)
    private UUID operatorId;
    @Id
    @Column(name = "ward_id", nullable = false)
    private UUID wardId;

    public static class Key implements java.io.Serializable {
        public UUID operatorId;
        public UUID wardId;

        public Key() {
        }

        public boolean equals(Object other) {
            return other instanceof Key k && Objects.equals(operatorId, k.operatorId) && Objects.equals(wardId, k.wardId);
        }

        public int hashCode() {
            return Objects.hash(operatorId, wardId);
        }
    }
}
