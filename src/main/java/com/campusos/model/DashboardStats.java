package com.campusos.model;

/**
 * Aggregated counters for the administrator dashboard.
 * Every value comes from a real COUNT(*) / aggregate query on MySQL.
 */
public class DashboardStats {

    private long totalStudents;
    private long totalFaculty;
    private long totalAdmins;
    private long totalUsers;
    private long totalDepartments;
    private long totalResources;
    private long totalBookings;
    private long pendingBookings;
    private long approvedBookings;
    private long totalComplaints;
    private long pendingComplaints;   // SUBMITTED + ASSIGNED + IN_PROGRESS
    private long resolvedComplaints;  // RESOLVED + CLOSED
    private long totalEvents;
    private long upcomingEvents;
    private long totalRegistrations;
    private long auditEntries;

    public long getTotalStudents() { return totalStudents; }
    public void setTotalStudents(long totalStudents) { this.totalStudents = totalStudents; }

    public long getTotalFaculty() { return totalFaculty; }
    public void setTotalFaculty(long totalFaculty) { this.totalFaculty = totalFaculty; }

    public long getTotalAdmins() { return totalAdmins; }
    public void setTotalAdmins(long totalAdmins) { this.totalAdmins = totalAdmins; }

    public long getTotalUsers() { return totalUsers; }
    public void setTotalUsers(long totalUsers) { this.totalUsers = totalUsers; }

    public long getTotalDepartments() { return totalDepartments; }
    public void setTotalDepartments(long totalDepartments) { this.totalDepartments = totalDepartments; }

    public long getTotalResources() { return totalResources; }
    public void setTotalResources(long totalResources) { this.totalResources = totalResources; }

    public long getTotalBookings() { return totalBookings; }
    public void setTotalBookings(long totalBookings) { this.totalBookings = totalBookings; }

    public long getPendingBookings() { return pendingBookings; }
    public void setPendingBookings(long pendingBookings) { this.pendingBookings = pendingBookings; }

    public long getApprovedBookings() { return approvedBookings; }
    public void setApprovedBookings(long approvedBookings) { this.approvedBookings = approvedBookings; }

    public long getTotalComplaints() { return totalComplaints; }
    public void setTotalComplaints(long totalComplaints) { this.totalComplaints = totalComplaints; }

    public long getPendingComplaints() { return pendingComplaints; }
    public void setPendingComplaints(long pendingComplaints) { this.pendingComplaints = pendingComplaints; }

    public long getResolvedComplaints() { return resolvedComplaints; }
    public void setResolvedComplaints(long resolvedComplaints) { this.resolvedComplaints = resolvedComplaints; }

    public long getTotalEvents() { return totalEvents; }
    public void setTotalEvents(long totalEvents) { this.totalEvents = totalEvents; }

    public long getUpcomingEvents() { return upcomingEvents; }
    public void setUpcomingEvents(long upcomingEvents) { this.upcomingEvents = upcomingEvents; }

    public long getTotalRegistrations() { return totalRegistrations; }
    public void setTotalRegistrations(long totalRegistrations) { this.totalRegistrations = totalRegistrations; }

    public long getAuditEntries() { return auditEntries; }
    public void setAuditEntries(long auditEntries) { this.auditEntries = auditEntries; }
}
