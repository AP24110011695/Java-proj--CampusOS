package com.campusos.model;

import java.time.LocalDateTime;

/**
 * An immutable audit-trail record of an important system activity.
 * Rows are only inserted, never updated or deleted through the app.
 */
public class AuditLog {

    private Long id;
    private Long userId;            // null for system-level actions
    private String userName;        // joined for display
    private String action;          // e.g. BOOKING_APPROVED
    private String entityType;      // e.g. BOOKING
    private Long entityId;
    private String description;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
