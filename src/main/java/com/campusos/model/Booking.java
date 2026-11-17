package com.campusos.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * A request to reserve a resource for a time range on a specific date.
 * Status flow: PENDING → APPROVED / REJECTED / CANCELLED, APPROVED → COMPLETED.
 */
public class Booking {

    private Long id;
    private Long resourceId;
    private String resourceName;    // joined for display
    private ResourceType resourceType;
    private Long userId;
    private String userName;        // joined for display
    private String userEmail;       // joined for display
    private LocalDate bookingDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String purpose;
    private BookingStatus status;
    private LocalDateTime requestedAt;
    private LocalDateTime decidedAt;
    private Long decidedById;
    private String decidedByName;   // joined for display
    private String decisionRemarks;
    private LocalDateTime cancelledAt;

    public Booking() {
        this.status = BookingStatus.PENDING;
    }

    /** True when this booking occupies the requested slot (pending or approved). */
    public boolean blocksSlot() {
        return status == BookingStatus.PENDING || status == BookingStatus.APPROVED;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getResourceId() { return resourceId; }
    public void setResourceId(Long resourceId) { this.resourceId = resourceId; }

    public String getResourceName() { return resourceName; }
    public void setResourceName(String resourceName) { this.resourceName = resourceName; }

    public ResourceType getResourceType() { return resourceType; }
    public void setResourceType(ResourceType resourceType) { this.resourceType = resourceType; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public LocalDate getBookingDate() { return bookingDate; }
    public void setBookingDate(LocalDate bookingDate) { this.bookingDate = bookingDate; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }

    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }

    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }

    public LocalDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }

    public Long getDecidedById() { return decidedById; }
    public void setDecidedById(Long decidedById) { this.decidedById = decidedById; }

    public String getDecidedByName() { return decidedByName; }
    public void setDecidedByName(String decidedByName) { this.decidedByName = decidedByName; }

    public String getDecisionRemarks() { return decisionRemarks; }
    public void setDecisionRemarks(String decisionRemarks) { this.decisionRemarks = decisionRemarks; }

    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
}
