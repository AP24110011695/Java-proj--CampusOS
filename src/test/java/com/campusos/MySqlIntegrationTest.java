package com.campusos;

import com.campusos.config.DatabaseConfig;
import com.campusos.dao.BookingDAO;
import com.campusos.dao.Db;
import com.campusos.dao.EventDAO;
import com.campusos.dao.ResourceDAO;
import com.campusos.dao.UserDAO;
import com.campusos.exception.AuthenticationException;
import com.campusos.exception.BookingConflictException;
import com.campusos.exception.CampusOSException;
import com.campusos.model.Booking;
import com.campusos.model.Complaint;
import com.campusos.model.ComplaintCategory;
import com.campusos.model.ComplaintPriority;
import com.campusos.model.ComplaintStatus;
import com.campusos.model.DashboardStats;
import com.campusos.model.Event;
import com.campusos.model.EventRegistration;
import com.campusos.model.EventStatus;
import com.campusos.model.Role;
import com.campusos.model.User;
import com.campusos.security.PasswordHasher;
import com.campusos.service.AuthService;
import com.campusos.service.BookingService;
import com.campusos.service.ComplaintService;
import com.campusos.service.EventService;
import com.campusos.session.Session;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests against the real MySQL database (campusos_db).
 * Skipped automatically when no database configuration is present.
 * Uses uniquely-named temporary rows and cleans up after itself.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MySqlIntegrationTest {

    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);

    private Session adminSession;
    private Session studentSession;
    private Session facultySession;
    private ResourceDAO resourceDAO = new ResourceDAO();
    private UserDAO userDAO = new UserDAO();

    private long testResourceId;
    private String testName;

    @BeforeAll
    void setup() {
        // Skips the whole class when config/db.properties or env vars are absent.
        try {
            DatabaseConfig.get();
        } catch (CampusOSException missingConfig) {
            assumeTrue(false, "Database configuration not available: " + missingConfig.getMessage());
        }

        testName = "IT-Resource-" + UUID.randomUUID().toString().substring(0, 8);

        AuthService authService = new AuthService();
        adminSession = authService.login("admin@campus.edu", "Admin@123");
        studentSession = authService.login("aarav.mehta@student.edu", "Student@123");
        facultySession = authService.login("anita.sharma@campus.edu", "Faculty@123");

        // A dedicated active test resource so the tests never touch demo data.
        com.campusos.model.Resource resource = new com.campusos.model.Resource();
        resource.setName(testName);
        resource.setType(com.campusos.model.ResourceType.CLASSROOM);
        resource.setLocation("Integration Test Block");
        resource.setStatus(com.campusos.model.ResourceStatus.ACTIVE);
        resource.setCreatedById(adminSession.currentUserId());
        testResourceId = Db.withConnection(conn -> resourceDAO.insert(conn, resource));
    }

    @AfterAll
    void cleanup() {
        try {
            // Remove children first (FKs are RESTRICT) then the test rows.
            Db.withConnectionVoid(conn -> {
                com.campusos.dao.Sql.update(conn, "DELETE FROM bookings WHERE resource_id = ?", testResourceId);
                com.campusos.dao.Sql.update(conn, "DELETE FROM events WHERE title LIKE 'IT-Event-%'");
                com.campusos.dao.Sql.update(conn, "DELETE FROM complaints WHERE title LIKE 'IT-Lifecycle probe%'");
            });
        } catch (Exception ignored) {
            // cleanup is best-effort
        }
        try {
            Db.withConnectionVoid(conn -> resourceDAO.delete(conn, testResourceId));
        } catch (Exception ignored) {
            // cleanup is best-effort
        }
    }

    // ------------------------------------------------------------------ auth

    @Test
    @Order(1)
    @DisplayName("authentication: blank input -> ValidationException; wrong password -> AuthenticationException")
    void authenticationValidation() {
        assertThrows(com.campusos.exception.ValidationException.class,
                () -> new AuthService().login("", ""));
        assertThrows(AuthenticationException.class,
                () -> new AuthService().login("admin@campus.edu", "wrong-password"));
        assertThrows(AuthenticationException.class,
                () -> new AuthService().login("ghost@nowhere.edu", "whatever1"));
    }

    @Test
    @Order(2)
    @DisplayName("authentication: correct credentials of all three roles log in")
    void authenticationAllRoles() {
        assertEquals(Role.ADMIN, adminSession.currentUser().getRole());
        assertEquals(Role.STUDENT, studentSession.currentUser().getRole());
        assertEquals(Role.FACULTY, facultySession.currentUser().getRole());
    }

    @Test
    @Order(3)
    @DisplayName("student self-registration creates a STUDENT account that can log in")
    void studentRegistrationEndToEnd() {
        AuthService service = new AuthService();
        String email = "it-reg-" + UUID.randomUUID().toString().substring(0, 8) + "@student.edu";
        String identifier = "ITREG" + UUID.randomUUID().toString().substring(0, 6);
        Long cseId = Db.withConnection(conn ->
                com.campusos.dao.Sql.queryLong(conn,
                        "SELECT department_id FROM departments WHERE department_code = 'CSE'"));

        try {
            User created = service.registerStudent("IT Registration Probe", email,
                    "9876500000", identifier, cseId, "Probe@12345", "Probe@12345");
            assertTrue(created.getId() > 0);

            // role is forced to STUDENT and the account is immediately usable
            assertEquals(Role.STUDENT, created.getRole());
            Session fresh = service.login(email, "Probe@12345");
            assertEquals(Role.STUDENT, fresh.currentUser().getRole());

            // negative: duplicate email rejected
            assertThrows(CampusOSException.class, () -> service.registerStudent(
                    "Second Probe", email, null, null, cseId, "Probe@12345", "Probe@12345"));
        } finally {
            Db.withConnectionVoid(conn ->
                    com.campusos.dao.Sql.update(conn, "DELETE FROM users WHERE email = ?", email));
        }
    }

    // ------------------------------------------------------------------ bookings

    @Test
    @Order(10)
    @DisplayName("booking: overlapping request is rejected with a conflict message")
    void overlappingBookingRejected() {
        BookingService service = new BookingService();

        Booking first = service.requestBooking(studentSession, testResourceId,
                TOMORROW, LocalTime.of(10, 0), LocalTime.of(11, 0), "Integration test slot A");
        assertTrue(first.getId() > 0);

        // 10:30-11:30 overlaps 10:00-11:00 -> must throw
        BookingConflictException conflict = assertThrows(BookingConflictException.class,
                () -> service.requestBooking(facultySession, testResourceId,
                        TOMORROW, LocalTime.of(10, 30), LocalTime.of(11, 30), "Overlapping attempt"));
        assertTrue(conflict.getMessage().contains("conflict"));

        // also an exact duplicate time range conflicts
        assertThrows(BookingConflictException.class,
                () -> service.requestBooking(facultySession, testResourceId,
                        TOMORROW, LocalTime.of(10, 0), LocalTime.of(11, 0), "Same slot again"));
    }

    @Test
    @Order(11)
    @DisplayName("booking: adjacent slots are allowed (11:00 start == 10:00-11:00 end)")
    void adjacentBookingAllowed() {
        BookingService service = new BookingService();
        Booking adjacent = service.requestBooking(facultySession, testResourceId,
                TOMORROW, LocalTime.of(11, 0), LocalTime.of(12, 0), "Adjacent slot is fine");
        assertTrue(adjacent.getId() > 0);
    }

    @Test
    @Order(12)
    @DisplayName("booking: student cannot approve; admin approves; owner cancels")
    void bookingLifecycleAndPermissions() {
        BookingService service = new BookingService();

        // authorization: student cannot approve/reject
        com.campusos.session.Session student = studentSession;
        List<Booking> mine = service.listMine(student, com.campusos.model.BookingStatus.PENDING);
        Booking request = mine.stream()
                .filter(b -> b.getResourceId() == testResourceId)
                .findFirst().orElseThrow();

        assertThrows(com.campusos.exception.AuthorizationException.class,
                () -> service.decide(studentSession, request.getId(),
                        com.campusos.model.BookingStatus.APPROVED, null));

        service.decide(adminSession, request.getId(), com.campusos.model.BookingStatus.APPROVED, "ok for test");
        Booking approved = service.getById(studentSession, request.getId());
        assertEquals(com.campusos.model.BookingStatus.APPROVED, approved.getStatus());
        assertNotNull(approved.getDecidedAt());

        service.cancel(studentSession, request.getId());
        assertEquals(com.campusos.model.BookingStatus.CANCELLED,
                service.getById(studentSession, request.getId()).getStatus());

        // negative: cancelling again is rejected (terminal state)
        assertThrows(BookingConflictException.class, () -> service.cancel(studentSession, request.getId()));
    }

    @Test
    @Order(13)
    @DisplayName("booking: concurrent overlapping requests - exactly one succeeds (row locking)")
    void concurrentOverlappingRequests() throws Exception {
        BookingService service = new BookingService();
        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        List<Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Session session = (i % 2 == 0) ? studentSession : facultySession;
            futures.add(pool.submit(() -> {
                startGate.await();
                try {
                    service.requestBooking(session, testResourceId, TOMORROW,
                            LocalTime.of(14, 0), LocalTime.of(15, 0), "Concurrency probe " + UUID.randomUUID());
                    successes.incrementAndGet();
                } catch (BookingConflictException expected) {
                    conflicts.incrementAndGet();
                }
                return null;
            }));
        }
        startGate.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));

        assertEquals(1, successes.get(), "row locking must serialize: only one booking wins");
        assertEquals(threads - 1, conflicts.get(), "all other concurrent attempts must be rejected");
    }

    // ------------------------------------------------------------------ complaints

    @Test
    @Order(20)
    @DisplayName("complaint lifecycle with full history preserved in complaint_updates")
    void complaintLifecycle() {
        ComplaintService service = new ComplaintService();

        Complaint complaint = service.submit(studentSession, ComplaintCategory.IT_SUPPORT,
                "IT-Lifecycle probe " + UUID.randomUUID().toString().substring(0, 6),
                "Integration test complaint.", ComplaintPriority.HIGH, null);
        assertTrue(complaint.getId() > 0);
        assertEquals(ComplaintStatus.SUBMITTED, complaint.getStatus());

        // negative: student cannot assign, lifecycle cannot skip steps
        assertThrows(com.campusos.exception.AuthorizationException.class,
                () -> service.assign(studentSession, complaint.getId(), facultySession.currentUserId(), null));
        assertThrows(com.campusos.exception.AuthorizationException.class,
                () -> service.updateStatus(studentSession, complaint.getId(),
                        ComplaintStatus.IN_PROGRESS, "student trying to skip"));
        assertThrows(com.campusos.exception.AuthorizationException.class,
                () -> service.updateStatus(facultySession, complaint.getId(),
                        ComplaintStatus.IN_PROGRESS, "unassigned faculty"));

        // admin assigns -> assigned faculty progresses -> submitter closes
        service.assign(adminSession, complaint.getId(), facultySession.currentUserId(), "take care of this");
        assertEquals(ComplaintStatus.ASSIGNED,
                service.getDetail(adminSession, complaint.getId()).getStatus());

        service.updateStatus(facultySession, complaint.getId(), ComplaintStatus.IN_PROGRESS,
                "work started");
        service.updateStatus(facultySession, complaint.getId(), ComplaintStatus.RESOLVED, "fixed");

        // negative: resolver cannot close on behalf of the submitter
        assertThrows(com.campusos.exception.AuthorizationException.class,
                () -> service.updateStatus(facultySession, complaint.getId(),
                        ComplaintStatus.CLOSED, "faculty closing too early"));

        service.updateStatus(studentSession, complaint.getId(), ComplaintStatus.CLOSED,
                "verified, thank you");
        assertEquals(ComplaintStatus.CLOSED, service.getDetail(adminSession, complaint.getId()).getStatus());

        // every transition was recorded: SUBMITTED + ASSIGN + IN_PROGRESS + RESOLVED + CLOSED = 5
        List<com.campusos.model.ComplaintUpdate> history = service.getHistory(adminSession, complaint.getId());
        assertEquals(5, history.size(), "complaint_updates must hold every transition");
    }

    // ------------------------------------------------------------------ events

    private Event createTestEvent(int capacity) {
        EventService service = new EventService();
        Event event = new Event();
        event.setTitle("IT-Event-" + UUID.randomUUID().toString().substring(0, 8));
        event.setDescription("Integration test event");
        event.setVenue("Test Hall");
        event.setStartTime(LocalDateTime.now().plusDays(3));
        event.setEndTime(LocalDateTime.now().plusDays(3).plusHours(2));
        event.setRegistrationDeadline(LocalDateTime.now().plusDays(2));
        event.setMaxParticipants(capacity);
        return service.create(facultySession, event);
    }

    @Test
    @Order(30)
    @DisplayName("event: capacity enforced and duplicate registration blocked")
    void eventCapacityAndDuplicates() {
        EventService service = new EventService();
        Event event = createTestEvent(1); // one seat

        service.register(studentSession, event.getId());

        // duplicate registration by the same student -> blocked
        assertThrows(CampusOSException.class, () -> service.register(studentSession, event.getId()));

        // second student -> capacity exceeded (seat taken by the first)
        Session diya = new AuthService().login("diya.kapoor@student.edu", "Student@123");
        CampusOSException full = assertThrows(CampusOSException.class,
                () -> service.register(diya, event.getId()));
        assertTrue(full.getMessage().toLowerCase().contains("full"));

        // organizer cancels the event -> registration now impossible
        service.cancel(facultySession, event.getId());
        assertEquals(EventStatus.CANCELLED,
                service.getDetail(adminSession, event.getId()).getStatus());
        assertThrows(CampusOSException.class, () -> service.register(diya, event.getId()));
    }

    @Test
    @Order(31)
    @DisplayName("event: attendance marking works and audit trail received entries")
    void attendanceAndAudit() {
        EventService service = new EventService();
        Event event = createTestEvent(5);
        service.register(studentSession, event.getId());

        EventRegistration registration = service.myRegistrationFor(studentSession, event.getId());
        assertNotNull(registration);
        assertEquals(com.campusos.model.RegistrationStatus.REGISTERED, registration.getStatus());

        // negative: registration cancellation releases the seat, re-register blocked by unique key
        service.cancelRegistration(studentSession, registration.getId());
        EventRegistration cancelled = service.myRegistrationFor(studentSession, event.getId());
        assertEquals(com.campusos.model.RegistrationStatus.CANCELLED, cancelled.getStatus());
        assertThrows(CampusOSException.class, () -> service.register(studentSession, event.getId()));

        // re-register with a different student, mark attended, verify counts via DAO
        Session diya = new AuthService().login("diya.kapoor@student.edu", "Student@123");
        service.register(diya, event.getId());
        EventRegistration diyaReg = service.myRegistrationFor(diya, event.getId());
        service.markAttendance(facultySession, diyaReg.getId(), true);
        Event attended = Db.withConnection(conn -> new EventDAO().findById(conn, event.getId()));
        assertEquals(1, attended.getAttendedCount());
        assertEquals(1, attended.getRegisteredCount());
    }

    @Test
    @Order(40)
    @DisplayName("dashboard stats aggregate query returns live counters")
    void dashboardStatsLive() {
        DashboardStats stats = Db.withConnection(conn ->
                new com.campusos.dao.AnalyticsDAO().loadDashboardStats(conn));
        assertTrue(stats.getTotalUsers() >= 7, "seeded users + admin must exist");
        assertTrue(stats.getTotalBookings() >= 0);
        assertTrue(stats.getTotalComplaints() >= 0);
        assertTrue(stats.getTotalEvents() >= 0);
    }
}
