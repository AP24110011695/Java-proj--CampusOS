# CampusOS — Manual Testing Checklist

Automated coverage: 60 JUnit tests (`mvn test`). This checklist covers the
manual, GUI-based verification of every role and the database-backed flows.
Tick items as you verify. All steps assume the sample data is loaded and an
administrator was bootstrapped.

**Demo accounts:** admin@campus.edu / Admin@123 · anita.sharma@campus.edu /
Faculty@123 · aarav.mehta@student.edu / Student@123

## 1. Environment

- [ ] `mvn clean compile` succeeds with no warnings that block the build
- [ ] `mvn test` → 60 tests, 0 failures (incl. MySqlIntegrationTest + FxmlSmokeTest)
- [ ] `mvn javafx:run` opens the login window
- [ ] With MySQL stopped, login shows a friendly connection error (no crash)

## 2. Authentication (all roles)

- [ ] Login with wrong password → "Invalid email or password." (audit entry written)
- [ ] Login with deactivated account → "account has been deactivated"
- [ ] Blank fields → validation banner
- [ ] Admin/Faculty/Student logins land on their role-specific dashboards
- [ ] Student self-registration: creates account, appears in admin's user table
- [ ] Duplicate email or student ID during registration → friendly error
- [ ] Logout returns to login; session is really gone (back navigation impossible)
- [ ] audit_logs shows LOGIN/LOGOUT/USER_REGISTERED entries

## 3. User management (admin)

- [ ] Add faculty member → appears with FACULTY badge; can log in
- [ ] Add administrator account → allowed from UI, role badge ADMIN
- [ ] Edit user (change department/phone) → changes persist after refresh
- [ ] Reset password → new password works, old one fails
- [ ] Deactivate a student → that student cannot log in; bookings kept (soft delete)
- [ ] Filter by role + department + search text → results correct
- [ ] CSV export opens in Excel with proper escaping (try a name containing a comma)

## 4. Departments (admin)

- [ ] Create department; duplicate name/code rejected
- [ ] Edit code; toggle active; delete empty department OK
- [ ] Delete department with users → blocked with clear message

## 5. Resource booking (student + faculty + admin)

- [ ] Student books Computer Lab 1 tomorrow 10:00–11:00 → PENDING
- [ ] Second attempt 10:30–11:30 same day → **conflict message**
- [ ] Attempt exactly 11:00–12:00 (adjacent) → allowed
- [ ] Rejected/cancelled bookings do not block new ones
- [ ] Booking a MAINTENANCE/INACTIVE resource → blocked
- [ ] Past date or end ≤ start → validation error
- [ ] Admin approves with remarks → status APPROVED, decided_by/decided_at set
- [ ] Admin rejects → REJECTED with remarks shown to requester
- [ ] Student cannot see approve/reject buttons (admin view only)
- [ ] Owner cancels PENDING → CANCELLED; cancelling again → blocked
- [ ] Cancel APPROVED before slot start → OK; after start → blocked
- [ ] Admin marks completed; bookings by status chart updates
- [ ] Booking CSV export

## 6. Complaints (student → admin → faculty → close)

- [ ] Student submits complaint (IT_SUPPORT) → SUBMITTED, appears in "My Complaints"
- [ ] Student cannot assign or progress (buttons hidden; service rejects too)
- [ ] Admin assigns to Prof. Anita Sharma → ASSIGNED; history row written
- [ ] Admin reassigns while ASSIGNED → history records the change
- [ ] Faculty moves ASSIGNED → IN_PROGRESS → RESOLVED (remarks mandatory)
- [ ] Non-assigned faculty cannot progress someone else's complaint
- [ ] Submitter closes the RESOLVED complaint → CLOSED
- [ ] Detail dialog shows the full timeline (5 rows: submitted/assign/in-progress/resolve/close)
- [ ] Filters: status, category, priority, department, text search
- [ ] Complaint CSV export

## 7. Events (admin/faculty create; student registers)

- [ ] Admin creates event (capacity 2, deadline tomorrow) → UPCOMING
- [ ] Faculty creates their own event; sees only theirs in Event Management
- [ ] Student A registers → seats 1/2; My Registrations shows REGISTERED
- [ ] Student A registers again → "already registered" (DB unique key)
- [ ] Fill remaining seat; Student C → "event is full. Registration closed automatically."
- [ ] Deadline passed (edit deadline to yesterday) → registration blocked
- [ ] Cancel registration → CANCELLED; re-register same event → blocked (by design)
- [ ] Organizer marks attendance → ATTENDED with timestamp; attendance chart updates
- [ ] Cancel event → registration attempts blocked; participants list preserved
- [ ] Event CSV export (registrations)

## 8. Dashboards & analytics

- [ ] Admin dashboard counters match SQL totals (`SELECT COUNT(*)` spot-check)
- [ ] Charts render from live data (create data → counts change after Refresh)
- [ ] Monthly booking trend shows current month after new bookings
- [ ] Faculty dashboard shows assigned complaints + own events + activity
- [ ] Student dashboard shows own bookings/complaints/events only (privacy)

## 9. Audit logs & profile

- [ ] Performing the steps above fills audit_logs (BOOKING_APPROVED,
      COMPLAINT_ASSIGNED, EVENT_REGISTERED, CSV exports, ...)
- [ ] Filter by action/user works; entries are read-only
- [ ] Profile: update phone → persists; change password → re-login works with new one
- [ ] Change password with wrong current password → rejected

## 10. Cross-cutting

- [ ] Window resize: layout adapts (tables/charts grow with window)
- [ ] Every visible button does something (no dead controls)
- [ ] Database restart mid-session → next action shows friendly error, no crash
- [ ] `config/db.properties` is gitignored and absent from the repository
- [ ] No plain-text passwords anywhere in the database (`password_hash` starts `$2a$`)
