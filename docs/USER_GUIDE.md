# CampusOS — User Guide

A role-by-role manual for the desktop application.

## Getting started

1. Make sure MySQL is running and `config/db.properties` points at `campusos_db`.
2. Start the app: `mvn clean javafx:run`.
3. Sign in on the login screen (see your administrator for credentials).
   Students can self-register with **"Create an account"**.

The **left sidebar** is role-aware: you only see the modules your role permits.
The **top bar** shows who you are; **⎋ Sign out** returns to the login screen.

---

## Administrator

### Dashboard
Live campus statistics (users, resources, bookings, complaints, events,
registrations), two pie charts (bookings & complaints by status), the monthly
booking trend and a "awaiting your action" panel. Every number is queried live
from MySQL.

### User Management
* Filter by **role**, **department** or free-text search (name/email/ID).
* **＋ Add user** — create Student/Faculty/Administrator accounts (dialog with
  validation and optional password reset).
* **✎ Edit selected** — change details, role, department, status; leave the
  password field empty to keep the current password.
* **⏻ Deactivate/Activate** — soft delete (recommended when referenced data exists).
* **🔑 Reset password** — sets a new password for the selected account.
* **⇩ Export** — CSV of all students and faculty.

### Departments
Add/edit departments (name + unique code + description), toggle active/inactive,
delete (only when no users are attached — otherwise deactivate).

### Campus Resources
* Search by name/location and filter by type.
* **＋ Add / ✎ Edit** — name, type, location, capacity, description, status.
* **⏻ Toggle status** — ACTIVE → INACTIVE → MAINTENANCE → ACTIVE.
* Deleted only when no bookings reference the resource.

### Booking Approvals
* Filter by status/date, search by user or purpose.
* Select a PENDING row → **✓ Approve** or **✕ Reject** (optional remarks).
* Select an APPROVED row → **✔ Mark completed**.
* **⇩ Export CSV** of the full booking history.

### Complaint Management
* Filter by status, category, priority, department; search text.
* **👤 Assign to faculty** (opens detail dialog with a faculty picker) — also
  reassignable while ASSIGNED.
* **🔍 Open details & history** — the full timeline: every transition with who,
  when and remarks. Admins can also close RESOLVED complaints here.
* **⇩ Export CSV**.

### Event Management
* **＋ New event** — title, description, venue, start/end (date+time),
  registration deadline, capacity, optional department.
* **✎ Edit** (upcoming only), **✕ Cancel event** (upcoming only).
* **👥 Registrations & attendance** — participant list per event; select a
  REGISTERED participant and **✓ Mark attended** (or clear attendance).

### Analytics & Reports
Five database-driven charts (bookings by status, complaints by category, monthly
booking trend, users by department, event attendance) plus CSV export buttons for
students, faculty, bookings, complaints and event registrations.

### Audit Logs
Read-only trail of every important action (logins, bookings, approvals,
assignments, events, exports...). Filter by action text or user. No editing —
by design.

### My Profile
View account details, update name/email/phone, change password
(current password required).

---

## Faculty

### Dashboard
Assigned complaints (open), resolved count, your upcoming events, your bookings
and a personal activity feed.

### Campus Resources
Browse active resources and **🗓 Book selected** to request a slot.

### My Bookings
* **＋ New booking request** — pick resource, date, half-hour start/end, purpose.
  The system rejects overlapping time slots immediately (conflict message).
* Track statuses (PENDING/APPROVED/REJECTED/COMPLETED/CANCELLED) and admin remarks.
* **✕ Cancel selected** — pending any time; approved until the slot starts.

### My Complaints
* Submit complaints (category, priority, optional department, title, description).
* Track progress; open **details & history** for the full timeline.
* When you are assigned a complaint: open the detail dialog →
  **→ Start working on it (IN_PROGRESS)** → **→ Mark resolved (RESOLVED)**
  (remarks are mandatory and stored in history).

### Event Management
Create and manage **your own** events, view registrations, mark attendance.
(Admins see all events; faculty only the ones they organize.)

### Campus Events
Browse upcoming events and see registration counts.

---

## Student

### Dashboard
Pending/approved bookings, open/resolved complaints, upcoming registered events,
recent bookings/complaints and your activity.

### Campus Resources → Book selected
Request a resource for a date + time window with a purpose. Status starts at
**PENDING** until an administrator approves.

### My Bookings
Track and cancel eligible bookings (see above).

### My Complaints
Submit and track complaints; acknowledge (close) complaints once RESOLVED.

### Campus Events
* **Upcoming events** tab — browse, see seats taken, **✓ Register for selected
  event** (blocked when full, past deadline, or already registered).
* **My registrations** tab — cancel eligible registrations before the event starts.
  Note: after cancelling, re-registration for the same event is not possible
  (enforced by a database unique key).

### My Profile
Update contact details and password.

---

## Tips for the demo / viva

1. Show the **booking conflict**: as a student, book Computer Lab 1 tomorrow
   10:00–11:00; as another student, attempt 10:30–11:30 → conflict message.
2. Show the **complaint history**: admin assigns Diya's complaint, faculty moves it
   forward — every step appears in the detail dialog timeline.
3. Show **capacity enforcement**: create an event with capacity 1, register two
   students — the second is rejected ("The event is full").
4. Show **concurrency safety**: `mvn test` — the integration test fires 6
   simultaneous conflicting bookings and proves exactly one succeeds.
5. Show **analytics & CSV export** — real data, then open the CSV in Excel.
6. Show **audit logs** right after performing the actions above.
