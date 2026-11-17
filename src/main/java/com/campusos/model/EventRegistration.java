package com.campusos.model;

import java.time.LocalDateTime;

/**
 * One user's registration for one event. The database enforces
 * UNIQUE(event_id, user_id) so a user can never register twice.
 */
public class EventRegistration {

    private Long id;
    private Long eventId;
    private String eventTitle;      // joined for display
    private LocalDateTime eventStartTime; // joined for display
    private String venue;           // joined for display
    private Long userId;
    private String userName;        // joined for display
    private String userEmail;       // joined for display
    private RegistrationStatus status;
    private LocalDateTime registeredAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime attendedAt;

    /** True when this registration currently occupies a seat. */
    public boolean occupiesSeat() {
        return status == RegistrationStatus.REGISTERED || status == RegistrationStatus.ATTENDED;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }

    public String getEventTitle() { return eventTitle; }
    public void setEventTitle(String eventTitle) { this.eventTitle = eventTitle; }

    public LocalDateTime getEventStartTime() { return eventStartTime; }
    public void setEventStartTime(LocalDateTime eventStartTime) { this.eventStartTime = eventStartTime; }

    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public RegistrationStatus getStatus() { return status; }
    public void setStatus(RegistrationStatus status) { this.status = status; }

    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(LocalDateTime registeredAt) { this.registeredAt = registeredAt; }

    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }

    public LocalDateTime getAttendedAt() { return attendedAt; }
    public void setAttendedAt(LocalDateTime attendedAt) { this.attendedAt = attendedAt; }
}
