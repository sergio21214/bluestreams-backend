package com.spmf.observability;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "system_alert")
public class SystemAlert extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 128)
    public String fingerprint;

    @Column(length = 255)
    public String name;

    @Column(length = 32)
    public String status;

    @Column(length = 32)
    public String severity;

    @Column(length = 32)
    public String source;

    @Column(length = 1024)
    public String summary;

    @Column(length = 2000)
    public String description;

    @Column(name = "starts_at")
    public Instant startsAt;

    @Column(name = "ends_at")
    public Instant endsAt;

    @Column(name = "received_at", nullable = false)
    public Instant receivedAt;

    @Column(length = 2000)
    public String labels;
}
