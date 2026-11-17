# CampusOS — Architecture

## 1. Layered design

```
com.campusos
├── Main / Launcher          JavaFX bootstrap (login window → shell window)
├── controller/              JavaFX controllers - UI event handling ONLY
│   (20 controllers)         - no SQL, no JDBC imports
├── service/                 Business logic
│   ├── AuthService          login, student registration, password change
│   ├── UserService          admin CRUD, profile self-service
│   ├── DepartmentService    dept CRUD with reference guards
│   ├── ResourceService      resource CRUD + status
│   ├── BookingService       transactional booking workflow
│   ├── ComplaintService     lifecycle + history
│   ├── EventService         event CRUD + transactional registration
│   ├── AnalyticsService     dashboard/chart aggregates
│   ├── CsvExportService     report exports
│   ├── AuditService         append-only logging
│   ├── BootstrapService     first-admin creation
│   └── BookingRules / ComplaintRules / EventRules   PURE rule classes (JUnit-tested)
├── dao/                     JDBC persistence
│   ├── Db                   withConnection / withTransaction helpers
│   ├── Sql                  PreparedStatement toolkit (query/insert/update)
│   └── 10 DAO classes       SQL + ResultSet mappers
├── model/                   9 entities + 9 enums + stats DTOs + EventRules*
├── session/Session          current user + requireAdmin/requireAny guards
├── security/PasswordHasher  BCrypt wrapper
├── validation/Validator     central input validation
├── exception/               typed exceptions (Validation, Authentication,
│                            Authorization, BookingConflict, Capacity,
│                            DuplicateRegistration, NotFound, DataAccess)
├── util/                    Bg (FX threading), Alerts, CsvUtil, Fmt, Stylesheet
├── ui/                      View registry (sidebar), Navigator contract, Ui kit
├── tool/AdminBootstrap      secure first-admin CLI
└── config/DatabaseConfig    credential loading (env → config/db.properties)
```

\* `EventRules` sits in `service` (pure static checks, unit-tested).

### Dependency rule

`controller → service → dao → JDBC → MySQL`. Nothing skips a layer:
controllers never import `java.sql`, DAOs never import `javafx.*`.
Authorization lives in the service layer (`Session.requireAdmin()`,
`ComplaintRules.check(...)`, ...) so it cannot be bypassed from the UI.

## 2. JavaFX threading model

Every database call goes through `util/Bg.java`:

```java
Bg.run(() -> bookingService.listMine(session, null),   // background thread
       bookings -> table.setItems(...));               // FX Application Thread
```

* A `javafx.concurrent.Task` executes the service call on a daemon worker.
* `onSucceeded` delivers the result on the FX thread; `onFailed` shows a styled
  error dialog (optionally an inline banner via the 3-arg overload).
* The UI never blocks on JDBC; modal dialogs that need a retry loop (booking
  request, user/resource/event forms) use the event-consume pattern to keep the
  dialog open while validation runs asynchronously.

## 3. Navigation

`ui/View` is a single source of truth: every screen declares its title and the
roles allowed to open it. `ShellController` builds the sidebar from that enum,
loads the right FXML (role-specific dashboards), injects `Session` +
`Navigator` into the controller **before** `initialize()` runs, then calls
`onShow()` so every visit reloads fresh MySQL data. `Navigator.navigate(...)`
also guards role access at runtime.

## 4. Security model

| Concern | Implementation |
|---|---|
| Password storage | BCrypt (jBCrypt, cost 12); plain text never stored/logged |
| SQL injection | PreparedStatements with parameter binding in `Sql.bind` — no string-concatenated values |
| Credential management | env vars or gitignored `config/db.properties`; template committed, secrets not |
| Admin creation | No seeded admin; `AdminBootstrap` refuses once an admin exists |
| Authorization | Service-layer role checks (`Session.requireAdmin/requireAny`) + rule classes |
| Deactivated accounts | Login blocked; soft-delete everywhere |
| Audit trail | Append-only `audit_logs`, insert-only DAO, admin-visible read screen |
| Input validation | `Validator` centralizes policies; services call it before touching SQL |
| UI thread safety | `Bg` tasks; all mutations posted to the FX thread |

## 5. Error handling strategy

* DAOs wrap `SQLException` into `DataAccessException` (never leaks JDBC types upward).
* Business rules throw precise exceptions (`BookingConflictException`,
  `CapacityExceededException`, `DuplicateRegistrationException`,
  `AuthorizationException`, `ValidationException`) whose messages are shown
  verbatim to the user — they are written to be human-readable.
* `ValidationException` collects ALL problems in one pass (better UX than one
  error per submission).
* Unexpected errors surface with a stack-trace dialog (`Alerts.error(Throwable)`).

## 6. Design patterns used

| Pattern | Where |
|---|---|
| Layered architecture | whole project |
| DAO | `dao/*` |
| Data Mapper (row → object) | `*DAO.map(ResultSet)` |
| Functional transaction template | `Db.withTransaction` |
| MVC (JavaFX) | FXML view ↔ controller ↔ observable lists |
| Dependency injection (light) | shell injects `Session`+`Navigator` into controllers |
| Factory | `Ui.statCard/badge`, `Alerts` |
| Strategy via enums | `View.fxmlFor(role)` role-based dashboards |
| Template method (light) | `Sql.query/insert/update` around PreparedStatement lifecycle |
| CLI tool | `tool/AdminBootstrap` |

## 7. Build & runtime

* Maven 3.9, Java 21 (`maven.compiler.release=21`).
* `javafx-maven-plugin` runs the app with the JavaFX modules on the module path
  (`mvn javafx:run`); `Launcher` keeps `java -cp` launches possible too.
* MySQL Connector/J 8.4, jBCrypt 0.4, JUnit Jupiter 5.10 (test scope).
