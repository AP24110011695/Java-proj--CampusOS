# CampusOS — Smart Campus Management System

CampusOS is a **JavaFX + MySQL desktop application** for managing campus resources,
bookings, complaints, events and administration from one unified platform. It serves
three roles — **Administrator**, **Faculty** and **Student** — with role-based dashboards,
transactional business logic and live analytics.

> Academic project demonstrating Java OOP, JavaFX GUI + event handling, JDBC with
> PreparedStatement, MySQL relational design (JOINs, GROUP BY, subqueries, transactions,
> generated keys), BCrypt security and JUnit 5 testing.

---

## Table of contents

1. [Feature highlights](#feature-highlights)
2. [Technology stack](#technology-stack)
3. [Architecture overview](#architecture-overview)
4. [Prerequisites](#prerequisites)
5. [Database setup (MySQL)](#database-setup-mysql)
6. [Database configuration for the app](#database-configuration-for-the-app)
7. [Administrator bootstrap](#administrator-bootstrap)
8. [Build and run](#build-and-run)
9. [Run the tests](#run-the-tests)
10. [Demo accounts & sample data](#demo-accounts--sample-data)
11. [Project structure](#project-structure)
12. [Key business rules](#key-business-rules)
13. [Screenshots](#screenshots)
14. [Troubleshooting](#troubleshooting)

---

## Feature highlights

| Module | What it does | "Outstanding" extras |
|---|---|---|
| **1. Authentication & user management** | Login, student self-registration, admin-managed faculty/admin accounts, departments | BCrypt password hashing, soft-delete (deactivation), role/department filters, unique email + student/employee IDs |
| **2. Resource booking** | Browse resources, request time slots, admin approves/rejects | **Conflict-free booking**: overlap rule `newStart < existingEnd AND newEnd > existingStart` enforced inside a MySQL transaction with `SELECT ... FOR UPDATE` row locking — safe under concurrency |
| **3. Complaint management** | Students/faculty submit, admins assign, faculty resolve | Full lifecycle `SUBMITTED → ASSIGNED → IN_PROGRESS → RESOLVED → CLOSED` with an **append-only `complaint_updates` history** and role-validated transitions |
| **4. Event management** | Create/browse events, register, attendance | **Smart registration**: duplicate prevention (DB unique key), capacity enforcement via row locking, registration deadlines, attendance tracking |
| **5. Analytics & administration** | Role dashboards, JavaFX charts, CSV export, audit logs | Every number/chart comes from live SQL aggregates (COUNT/GROUP BY/subqueries) — no hardcoded data |

---

## Technology stack

| Component | Technology |
|---|---|
| Language | Java 21 (LTS) |
| Desktop GUI | JavaFX 21 (FXML + CSS, Scene Builder compatible) |
| Database | MySQL 8.0 (InnoDB) |
| Connectivity | JDBC via MySQL Connector/J 8.4 |
| Password hashing | jBCrypt 0.4 |
| Build | Maven 3.9+ |
| Testing | JUnit 5.10 |

---

## Architecture overview

Clean layered architecture — controllers never touch SQL:

```
┌────────────────────────────────────────────────────┐
│ Presentation:  FXML views + JavaFX controllers     │
│                (event handlers, tables, charts)    │
├────────────────────────────────────────────────────┤
│ Service:       AuthService, BookingService, ...    │
│                + pure rule classes (BookingRules,  │
│                ComplaintRules, EventRules)         │
│                validation, authorization,          │
│                transactions                        │
├────────────────────────────────────────────────────┤
│ DAO:           UserDAO, BookingDAO, ... on a tiny  │
│                JDBC toolkit (Sql + Db helpers)     │
│                PreparedStatements only             │
├────────────────────────────────────────────────────┤
│ MySQL:         campusos_db (9 tables, InnoDB)      │
└────────────────────────────────────────────────────┘
```

* **`Db.withTransaction(...)`** wraps booking/registration flows in MySQL transactions
  (autoCommit off → work → COMMIT, ROLLBACK on any failure).
* **Row locking** (`SELECT ... FOR UPDATE`) serializes concurrent bookings per resource
  and concurrent event registrations per event.
* **`Bg.run(...)`** runs every database call on a background thread so the UI never
  freezes; results return to the JavaFX Application Thread.

Full details: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) ·
Database design: [docs/DATABASE_DESIGN.md](docs/DATABASE_DESIGN.md) ·
ER diagram: [docs/ER_DIAGRAM.md](docs/ER_DIAGRAM.md)

---

## Prerequisites

1. **JDK 21** (Eclipse Temurin recommended) — `java -version` must show 21.x
2. **Maven 3.9+** — `mvn -version`
3. **MySQL Server 8.0** running locally (or any reachable MySQL 8 instance)
4. (Optional) **MySQL Workbench** or the `mysql` CLI to run the SQL scripts
5. (Optional) **Scene Builder** if you want to edit the FXML views visually

---

## Database setup (MySQL)

Run the two scripts in order (as a MySQL user with CREATE rights, e.g. root):

```bash
# 1. Creates the database campusos_db and all 9 tables
mysql -u root -p < database/schema.sql

# 2. Optional demo data (departments, users, resources, bookings, complaints, events)
mysql -u root -p < database/sample_data.sql
```

> `sample_data.sql` contains **no administrator account** by design.
> Faculty/student demo accounts use the passwords listed in
> [Demo accounts](#demo-accounts--sample-data).

---

## Database configuration for the app

The app reads credentials from (in priority order):

1. Environment variables `CAMPUSOS_DB_URL`, `CAMPUSOS_DB_USER`, `CAMPUSOS_DB_PASSWORD`
2. File `config/db.properties` (relative to the project/working directory)
3. File `~/.campusos/db.properties`

Copy the template and edit it — **never commit your real credentials**
(`config/db.properties` is already in `.gitignore`):

```bash
cp config/db.properties.example config/db.properties
```

```properties
db.url=jdbc:mysql://127.0.0.1:3306/campusos_db?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8
db.username=campusos_dev
db.password=YOUR_PASSWORD
```

---

## Administrator bootstrap

For security, **no default admin ships with the project**. Create the first
administrator once with the interactive CLI tool:

```bash
mvn compile exec:java -Dexec.mainClass=com.campusos.tool.AdminBootstrap
```

or with arguments:

```bash
mvn compile exec:java -Dexec.mainClass=com.campusos.tool.AdminBootstrap ^
     -Dexec.args="--name 'System Administrator' --email admin@campus.edu --password 'Admin@123'"
```

The tool refuses to run once any administrator exists, and the password policy
(min 8 chars, letters + digits) is enforced. Additional admins are created from
the in-app **User Management** screen.

---

## Build and run

```bash
# compile
mvn clean compile

# run the desktop application (recommended)
mvn clean javafx:run

# build a jar (classes + resources)
mvn clean package
```

`mvn javafx:run` puts the JavaFX modules on the module path automatically.
The entry point is `com.campusos.Launcher` → `com.campusos.Main`.

---

## Run the tests

```bash
mvn test
```

60 tests run:

* **Unit tests** (no database): password hashing, booking overlap math, booking
  transitions & authorization, complaint lifecycle rules, event capacity/duplicate
  rules, CSV escaping, input validation.
* **Integration tests** (`MySqlIntegrationTest`, needs the DB configured): real
  conflict detection, **concurrent booking race (6 threads → exactly 1 winner)**,
  complaint lifecycle history, event capacity, duplicate registration, attendance,
  login validation. These tests skip automatically when no DB config is present.

A new **FXML smoke test** loads and off-screen-renders every screen for every role
through the same sequence the app uses - broken views now fail in `mvn test`, not at runtime.

A manual, role-by-role QA checklist: [docs/TESTING_CHECKLIST.md](docs/TESTING_CHECKLIST.md)

---

## Demo accounts & sample data

After loading `sample_data.sql`:

| Role | Email | Password |
|---|---|---|
| Faculty | `anita.sharma@campus.edu` | `Faculty@123` |
| Faculty | `rahul.verma@campus.edu` | `Faculty@123` |
| Student | `aarav.mehta@student.edu` | `Student@123` |
| Student | `diya.kapoor@student.edu` | `Student@123` |
| Student | `kabir.singh@student.edu` | `Student@123` |
| Student | `meera.iyer@student.edu` | `Student@123` |

Administrator: created by you via the bootstrap tool (example in the command above).
Students can also self-register from the login screen ("Create an account").

---

## Project structure

```
campusos/
├── pom.xml                        Maven build (JavaFX, MySQL, jBCrypt, JUnit)
├── config/
│   ├── db.properties.example      Credential template (commit this)
│   └── db.properties              Your local credentials (gitignored)
├── database/
│   ├── schema.sql                 9-table schema, InnoDB, FKs, indexes, CHECKs
│   └── sample_data.sql            Demo departments/users/resources/bookings/...
├── docs/
│   ├── ARCHITECTURE.md            Layered design + threading + security
│   ├── DATABASE_DESIGN.md         Tables, relationships, SQL feature map
│   ├── ER_DIAGRAM.md              Entity-relationship diagram (Mermaid)
│   ├── USER_GUIDE.md              Role-by-role manual
│   ├── TESTING_CHECKLIST.md       Manual QA checklist per role
│   └── FEATURE_COMPLETION.md      Requirement → implementation traceability
├── src/main/java/com/campusos/
│   ├── Main.java / Launcher.java  JavaFX entry point
│   ├── config/                    DatabaseConfig (env vars / external file)
│   ├── controller/                20 JavaFX controllers (FXML-bound)
│   ├── dao/                       Db, Sql + 10 DAO classes (JDBC)
│   ├── exception/                 Typed exception hierarchy
│   ├── model/                     Entities + enums + stats DTOs
│   ├── security/                  PasswordHasher (BCrypt)
│   ├── service/                   Business services + pure rule classes
│   ├── session/                   Session (current user + role checks)
│   ├── tool/                      AdminBootstrap CLI
│   ├── ui/                        View registry, Navigator contract, Ui kit
│   ├── util/                      Bg (threads), Alerts, CsvUtil, Fmt, Stylesheet
│   └── validation/                Validator
├── src/main/resources/
│   ├── fxml/                      19 screens + 5 dialogs (Scene Builder ready)
│   └── css/app.css                Design system (SaaS-inspired theme)
└── src/test/java/com/campusos/    7 JUnit 5 test classes (60 tests)
```

---

## Key business rules

**Booking conflict (Module 2)** — a new request for `resource R, date D, [s,e)` is
rejected when an existing `PENDING/APPROVED` booking of `R` on `D` satisfies
`newStart < existingEnd AND newEnd > existingStart`. The check and the insert run in
**one transaction**; the resource row is locked with `SELECT ... FOR UPDATE` first, so
two simultaneous requests cannot both pass the check. Rejected/cancelled bookings do
not block slots. Touching slots (11:00 end / 11:00 start) are allowed.

**Complaint lifecycle (Module 3)** — every status change is validated against the
transition map + role rules and appended to `complaint_updates` (previous status,
new status, who, remarks, timestamp). Statuses are never silently overwritten.

**Event registration (Module 4)** — inside one transaction the event row is locked,
then checked: not full, before deadline, event upcoming, no existing registration
(unique `(event_id, user_id)` index is the DB-level backstop). Attendance per
participant can be marked by the organizer/admin.

**Security** — BCrypt-hashed passwords, PreparedStatements everywhere (no SQL
injection), role checks at the service layer (UI checks are cosmetic), credentials in
a gitignored config file, no default admin account, append-only audit logs.

---

## Screenshots

Add your own screenshots here after running the app:

| Screen | File |
|---|---|
| Login | `docs/screenshots/login.png` |
| Admin dashboard (charts) | `docs/screenshots/dashboard-admin.png` |
| Booking approvals | `docs/screenshots/bookings-admin.png` |
| Complaint detail + history | `docs/screenshots/complaint-detail.png` |
| Event registrations & attendance | `docs/screenshots/event-participants.png` |
| Analytics | `docs/screenshots/analytics.png` |

---

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| "Database configuration not found" dialog at startup | Copy `config/db.properties.example` to `config/db.properties` and fill it in (see above). |
| `Communications link failure` / `Connection refused` | MySQL is not running. Start the `MySQL80` Windows service: `net start MySQL80`. |
| `Access denied for user ...` | Wrong username/password in `config/db.properties`. |
| `Public Key Retrieval is not allowed` | Keep `allowPublicKeyRetrieval=true` in `db.url` (already in the template). |
| `Table 'campusos_db.users' doesn't exist` | Run `database/schema.sql` first, then bootstrap an admin. |
| "An administrator account already exists" during bootstrap | Expected — use User Management in the app instead. |
| Time conflict! message when booking | Working as intended — the slot overlaps a pending/approved booking. |
| Tests skipped (`MySqlIntegrationTest`) | No DB config visible to the test JVM; create `config/db.properties`. |
| JavaFX runtime components are missing | Run through `mvn javafx:run` (handles the module path) rather than `java -jar`. |

---

*Built as an academic Java project — 2026.*
