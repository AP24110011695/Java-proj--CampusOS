package com.campusos.model;

import java.time.LocalDateTime;

/**
 * A campus event. Registrations are unique per (event, user) and capacity
 * is enforced at the service layer with row locking inside a transaction.
 */
public class Event {

    private Long id;
    private String title;
    private String description;
    private String venue;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime registrationDeadline;
    private int maxParticipants;
    private Long createdById;
    private String createdByName;   // joined: organizer name
    private Long departmentId;
    private String departmentName;  // joined for display
    private EventStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int registeredCount;    // joined aggregate for display
    private int attendedCount;      // joined aggregate for display

    public Event() {
        this.status = EventStatus.UPCOMING;
    }

    /** Seats still available, counting REGISTERED and ATTENDED registrations. */
    public int seatsAvailable() {
        return Math.max(0, maxParticipants - registeredCount);
    }

    public boolean isRegistrationOpen(LocalDateTime now) {
        return status == EventStatus.UPCOMING
                && !now.isAfter(registrationDeadline)
                && registeredCount < maxParticipants;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public LocalDateTime getRegistrationDeadline() { return registrationDeadline; }
    public void setRegistrationDeadline(LocalDateTime registrationDeadline) { this.registrationDeadline = registrationDeadline; }

    public int getMaxParticipants() { return maxParticipants; }
    public void setMaxParticipants(int maxParticipants) { this.maxParticipants = maxParticipants; }

    public Long getCreatedById() { return createdById; }
    public void setCreatedById(Long createdById) { this.createdById = createdById; }

    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }

    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

    public EventStatus getStatus() { return status; }
    public void setStatus(EventStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public int getRegisteredCount() { return registeredCount; }
    public void setRegisteredCount(int registeredCount) { this.registeredCount = registeredCount; }

    public int getAttendedCount() { return attendedCount; }
    public void setAttendedCount(int attendedCount) { this.attendedCount = attendedCount; }
}
