package com.tchelo.ticket_service_ticket241.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Long id;

    @Column(unique = true, nullable = false, length = 36)
    protected String uuid;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    protected LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    protected LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    protected String createdBy;

    @Column(name = "updated_by", length = 100)
    protected String updatedBy;

    @Column(name = "is_archived")
    protected Boolean isArchived = false;

    @Column(name = "archived_at")
    protected LocalDateTime archivedAt;

    @Column(name = "unarchived_at")
    protected LocalDateTime unarchivedAt;

    @Column(name = "archived_by", length = 100)
    protected String archivedBy;
}
