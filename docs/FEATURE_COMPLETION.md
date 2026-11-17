# CampusOS — Feature Completion Report

Traceability from the assignment requirements to the implementation.
All items below are **implemented, compiled and tested** (`mvn test` = 55/55).

## Evaluation target

| Requirement | Status | Where |
|---|---|---|
| JavaFX desktop GUI | ✅ | JavaFX 21, 19 FXML screens + 5 FXML dialogs, `com.campusos.controller` |
| Event handling | ✅ | Every button wired to real handlers; selection listeners drive button states; property listeners update summaries |
| MySQL database | ✅ | MySQL 8, `campusos_db`, 9 InnoDB tables |
| Rich database operations | ✅ | INSERT/SELECT/UPDATE/DELETE, INNER+LEFT JOIN, GROUP BY, ORDER BY, COUNT/SUM, subqueries, transactions, PreparedStatement, generated keys, ResultSet mappers — see docs/DATABASE_DESIGN.md §3 |
| Complete committed features | ✅ | Traceability below |

## Module 1 — Authentication & user management

- [x] Login/logout with BCrypt verification (AuthService)
- [x] Student self-registration with uniqueness checks (email + student ID)
- [x] Admin creates/edits/deactivates/searches students & faculty & admins
- [x] Role & department filters, free-text search
- [x] Department management (create/edit/toggle/delete-with-guard)
- [x] Unique email + identifier constraints at DB level
- [x] Profile self-service + password change (old password verified)
- [x] No plain-text passwords; no default admin; secure bootstrap CLI

## Module 2 — Resource booking

- [x] Resource catalog (6 types) with status management
- [x] Browse + search; booking dialog (date, half-hour slots, purpose)
- [x] **Conflict detection**: `newStart < existingEnd AND newEnd > existingStart`
      against PENDING+APPROVED, inside a transaction with `FOR UPDATE` row locking
- [x] Rejected/cancelled bookings don't block slots; adjacent slots allowed
- [x] Statuses: PENDING/APPROVED/REJECTED/CANCELLED/COMPLETED + timestamps + decider
- [x] Approve/reject with remarks (admin), cancel eligibility rules, mark completed
- [x] Admin booking browser with filters + CSV export

## Module 3 — Complaint management

- [x] Submission (category, priority, department, title, description)
- [x] Lifecycle SUBMITTED → ASSIGNED → IN_PROGRESS → RESOLVED → CLOSED,
      validated by `ComplaintRules` (structure + role)
- [x] `complaint_updates` history: previous status, new status, updater, remarks,
      timestamp — never overwritten
- [x] Admin assignment/reassignment to active faculty; faculty resolve flow;
      submitter acknowledgement (close)
- [x] Filters: status/category/priority/department/text; priority-ordered lists
- [x] Complaint CSV export

## Module 4 — Event management

- [x] Event CRUD (title, description, venue, start/end, deadline, capacity,
      organizer, department) with validation incl. DB CHECK constraints
- [x] Faculty manage their own events only; admins manage all
- [x] **Duplicate registration prevention** (DB unique key + rule check)
- [x] **Capacity enforcement** with row-locked counting (concurrency-safe)
- [x] **Automatic closure** when full / deadline passed / event not upcoming
- [x] Registration cancellation (seat released; re-register blocked by unique key)
- [x] Attendance tracking per participant + attendance overview chart

## Module 5 — Analytics & administration

- [x] Role dashboards: admin (16 counters + 2 pies + trend + action panel),
      faculty (4 KPIs + assigned complaints + events + activity),
      student (5 KPIs + bookings + complaints + events + activity)
- [x] JavaFX charts from live aggregates: bookings by status (Pie),
      complaints by status/category (Pie), monthly booking trend (Line),
      users by department (Bar, LEFT JOIN+GROUP BY), event attendance (Bar, SUM)
- [x] No hardcoded numbers anywhere
- [x] CSV exports: students, faculty, bookings, complaints, event registrations
      (RFC-4180 escaping + formula-injection guard, FileChooser destination)
- [x] Audit logs: append-only, admin-only view, filters, covers registrations,
      logins, bookings, approvals, assignments, events, attendance, exports

## Security & reliability requirements

- [x] BCrypt hashing (cost 12) — `security/PasswordHasher`
- [x] PreparedStatements for 100% of queries — `dao/Sql`
- [x] Role-based authorization enforced in services, not just UI
- [x] Input validation (`validation/Validator`) incl. password policy
- [x] Credentials from env vars / gitignored external file; template provided
- [x] Transactions for critical operations — `dao/Db.withTransaction`
- [x] try-with-resources everywhere JDBC is touched
- [x] Graceful DB failure handling (friendly dialogs, no crashes)
- [x] `.gitignore` covers `config/db.properties`
- [x] Secure admin bootstrap (`tool/AdminBootstrap`) — refuses once admin exists

## Testing requirements

- [x] JUnit 5, 60 tests: hashing (6), validation (6), booking rules (11),
      complaint rules (7), event rules (8), CSV escaping (7), MySQL integration (10)
- [x] Negative scenarios: wrong password, terminal-status transitions, role
      violations, capacity full, deadline passed, duplicate registration, past dates
- [x] Concurrency proof: 6 parallel conflicting bookings → exactly 1 succeeds
- [x] Documented manual QA checklist (docs/TESTING_CHECKLIST.md)

## Documentation requirements

- [x] README with setup/build/run/test/troubleshooting (no assumed knowledge)
- [x] Database schema + sample data SQL files
- [x] ER diagram (docs/ER_DIAGRAM.md), database design (docs/DATABASE_DESIGN.md)
- [x] Architecture (docs/ARCHITECTURE.md), user guide (docs/USER_GUIDE.md)
- [x] Testing checklist (docs/TESTING_CHECKLIST.md), this report
