package com.campusos.model;

import java.time.LocalDateTime;

/**
 * One immutable entry of the complaint status history.
 * A row is inserted on every status change (never overwrite history).
 */
public class ComplaintUpdate {

    private Long id;
    private Long complaintId;
    private Long updatedById;
    private String updatedByName;   // joined for display
    private ComplaintStatus previousStatus;
    private ComplaintStatus newStatus;
    private String remarks;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getComplaintId() { return complaintId; }
    public void setComplaintId(Long complaintId) { this.complaintId = complaintId; }

    public Long getUpdatedById() { return updatedById; }
    public void setUpdatedById(Long updatedById) { this.updatedById = updatedById; }

    public String getUpdatedByName() { return updatedByName; }
    public void setUpdatedByName(String updatedByName) { this.updatedByName = updatedByName; }

    public ComplaintStatus getPreviousStatus() { return previousStatus; }
    public void setPreviousStatus(ComplaintStatus previousStatus) { this.previousStatus = previousStatus; }

    public ComplaintStatus getNewStatus() { return newStatus; }
    public void setNewStatus(ComplaintStatus newStatus) { this.newStatus = newStatus; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
