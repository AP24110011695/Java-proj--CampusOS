# CampusOS — Database Design

Database: **`campusos_db`** (MySQL 8, `utf8mb4`, InnoDB for every table).
Schema: [`database/schema.sql`](../database/schema.sql) · Demo data:
[`database/sample_data.sql`](../database/sample_data.sql)

## 1. Tables

| # | Table | Purpose | Key columns |
|---|---|---|---|
| 1 | `departments` | Academic departments | `department_name` (UNIQUE), `department_code` (UNIQUE), `is_active` |
| 2 | `users` | Admins, faculty, students | `email` (UNIQUE), `password_hash` (BCrypt), `role` ENUM, `department_id` FK, `identifier` (UNIQUE roll/employee no.), `is_active` |
| 3 | `resources` | Bookable campus assets | `resource_type` ENUM, `status` ENUM, `capacity`, `created_by` FK |
| 4 | `bookings` | Resource reservations | `resource_id` FK, `user_id` FK, `booking_date`, `start_time`, `end_time`, `status` ENUM, `decided_by` FK, `decided_at`, `decision_remarks`, `cancelled_at` |
| 5 | `complaints` | Complaint registry | `user_id` FK (submitter), `assigned_to` FK, `department_id` FK, `category`/`priority`/`status` ENUMs, `resolved_at` |
| 6 | `complaint_updates` | Append-only status history | `complaint_id` FK (CASCADE), `updated_by` FK, `previous_status`, `new_status`, `remarks`, `created_at` |
| 7 | `events` | Campus events | `venue`, `start_time`, `end_time`, `registration_deadline`, `max_participants`, `created_by` FK (organizer), `status` ENUM |
| 8 | `event_registrations` | Per-user registrations | `event_id` FK, `user_id` FK, `status` ENUM, **UNIQUE (`event_id`,`user_id`)**, `attended_at` |
| 9 | `audit_logs` | Append-only activity trail | `user_id` FK (nullable), `action`, `entity_type`, `entity_id`, `description`, `created_at` |

## 2. Constraints and indexes

* Primary keys: `AUTO_INCREMENT` on every table (`INT/BIGINT UNSIGNED`).
* Foreign keys with deliberate deletion rules — see [ER_DIAGRAM.md](ER_DIAGRAM.md).
* Unique constraints: `users.email`, `users.identifier`, `departments.department_name`,
  `departments.department_code`, `event_registrations(event_id, user_id)`.
* `CHECK` constraints (MySQL 8.0.16+): booking `start_time < end_time`;
  event `end_time > start_time` and `registration_deadline <= start_time`.
* Hot-path index for conflict detection:
  `idx_bookings_conflict (resource_id, booking_date, status, start_time, end_time)`.
* Search/filter indexes on `role`, `status`, `department_id`, `assigned_to`,
  `booking_date`, `created_at` (audit timeline), etc.

## 3. Rich SQL operations demonstrated

The DAO layer (`com.campusos.dao`) deliberately showcases each operation the
evaluation requires — all via `PreparedStatement`:

| Operation | Where it lives |
|---|---|
| **INSERT** | every DAO `insert(...)` |
| **SELECT** | all `find...` methods |
| **UPDATE** | `update`, `setStatus`, `markDecision`, `updateStatus`, `updatePasswordHash` |
| **DELETE** | department/resource deletion (guarded), test cleanup |
| **INNER JOIN** | `users ⋈ departments`, `bookings ⋈ resources ⋈ users`, `complaints ⋈ users × 2`, `event_registrations ⋈ events ⋈ users` |
| **LEFT JOIN** | `departments LEFT JOIN users` (user distribution), `events LEFT JOIN registrations` (seat counts), every display JOIN for optional FKs (`decided_by`, `assigned_to`, `department`) |
| **GROUP BY** | `countGroupedByStatus` (bookings/complaints/events), `monthlyTrend`, `usersByDepartment`, `topEventsByRegistrations`, `eventAttendanceOverview` |
| **ORDER BY** | everywhere; complaint lists use `FIELD(priority,'CRITICAL',...)` for priority ordering |
| **COUNT / SUM** | dashboard totals; `SUM(status = 'ATTENDED')` per event |
| **Subqueries** | `loadDashboardStats` computes 16 counters with scalar subqueries in one query; `resources.countBookings` guards deletion |
| **Transactions** | `Db.withTransaction` around booking creation, booking decisions, complaint updates, event registration |
| **PreparedStatement** | 100% of statements (`Sql.bind` parameter binding) — SQL injection impossible |
| **Generated keys** | `Sql.insert(..., Statement.RETURN_GENERATED_KEYS)` returns every AUTO_INCREMENT id |
| **ResultSet processing** | typed row mappers per entity (`BookingDAO.map`, `UserDAO.map`, ...) |
| **Row locking** | `SELECT ... FOR UPDATE` on the resource row (booking) and event row (registration) |
| **CASE / FIELD / DATE_FORMAT** | monthly trend `DATE_FORMAT(booking_date,'%b %Y')`; complaint ordering |

## 4. The two transactional "outstanding" flows

### 4.1 Conflict-free booking (`BookingService.requestBooking`)

```
BEGIN
  SELECT * FROM resources WHERE resource_id = ? FOR UPDATE   -- row lock (serialize)
  SELECT conflicts WHERE resource_id = ? AND date = ?
        AND status IN ('PENDING','APPROVED')
        AND start_time < :newEnd AND end_time > :newStart    -- overlap rule
        FOR UPDATE
  if conflicts -> ROLLBACK, BookingConflictException
  INSERT INTO bookings (...)                                  -- generated key
COMMIT
```

Because the resource row is locked before the overlap query, two clients booking
the same slot concurrently are fully serialized — verified by the JUnit
concurrency test (6 threads, exactly 1 success).

### 4.2 Capacity-safe event registration (`EventService.register`)

```
BEGIN
  SELECT * FROM events WHERE event_id = ? FOR UPDATE          -- row lock
  read existing registration of this user
  check: UPCOMING, deadline not passed, no active registration,
         registeredCount < maxParticipants
  INSERT INTO event_registrations (event_id, user_id, 'REGISTERED')
        -- UNIQUE(event_id, user_id) backstop catches races (error 1062)
COMMIT
```

## 5. Status vocabularies

| Table | Values |
|---|---|
| `users.role` | ADMIN, FACULTY, STUDENT |
| `resources.status` | ACTIVE, INACTIVE, MAINTENANCE |
| `bookings.status` | PENDING → APPROVED/REJECTED/CANCELLED; APPROVED → COMPLETED/CANCELLED |
| `complaints.status` | SUBMITTED → ASSIGNED → IN_PROGRESS → RESOLVED → CLOSED |
| `events.status` | UPCOMING, ONGOING, COMPLETED, CANCELLED |
| `event_registrations.status` | REGISTERED, CANCELLED, ATTENDED |
