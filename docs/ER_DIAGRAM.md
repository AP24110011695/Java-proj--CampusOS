# CampusOS — ER Diagram

## Entity-relationship overview (Mermaid)

```mermaid
erDiagram
    DEPARTMENTS ||--o{ USERS : "employs / enrols"
    USERS ||--o{ BOOKINGS : "requests"
    RESOURCES ||--o{ BOOKINGS : "is booked via"
    USERS ||--o{ RESOURCES : "creates (admin)"
    USERS ||--o{ BOOKINGS : "decides (admin)"
    USERS ||--o{ COMPLAINTS : "submits"
    DEPARTMENTS ||--o{ COMPLAINTS : "relates to"
    USERS ||--o{ COMPLAINTS : "is assigned"
    COMPLAINTS ||--o{ COMPLAINT_UPDATES : "has history"
    USERS ||--o{ COMPLAINT_UPDATES : "writes"
    USERS ||--o{ EVENTS : "organizes"
    DEPARTMENTS ||--o{ EVENTS : "belongs to"
    EVENTS ||--o{ EVENT_REGISTRATIONS : "receives"
    USERS ||--o{ EVENT_REGISTRATIONS : "registers"
    USERS ||--o{ AUDIT_LOGS : "performs"

    DEPARTMENTS {
        int department_id PK
        varchar department_name UK
        varchar department_code UK
        tinyint is_active
    }
    USERS {
        int user_id PK
        varchar email UK
        varchar password_hash "BCrypt"
        enum role "ADMIN|FACULTY|STUDENT"
        int department_id FK "nullable"
        varchar identifier UK "student/employee id"
        tinyint is_active "soft delete"
    }
    RESOURCES {
        int resource_id PK
        varchar resource_name
        enum resource_type
        enum status "ACTIVE|INACTIVE|MAINTENANCE"
        int created_by FK "nullable"
    }
    BOOKINGS {
        int booking_id PK
        int resource_id FK
        int user_id FK
        date booking_date
        time start_time
        time end_time
        enum status "PENDING|APPROVED|REJECTED|CANCELLED|COMPLETED"
        int decided_by FK "nullable"
        datetime decided_at
        varchar decision_remarks
    }
    COMPLAINTS {
        int complaint_id PK
        int user_id FK "submitter"
        int department_id FK "nullable"
        int assigned_to FK "nullable"
        enum category
        enum priority "LOW..CRITICAL"
        enum status "SUBMITTED..CLOSED"
        datetime resolved_at
    }
    COMPLAINT_UPDATES {
        int update_id PK
        int complaint_id FK "CASCADE"
        int updated_by FK
        enum previous_status "nullable"
        enum new_status
        varchar remarks
        datetime created_at
    }
    EVENTS {
        int event_id PK
        varchar title
        varchar venue
        datetime start_time
        datetime end_time
        datetime registration_deadline
        int max_participants
        int created_by FK "organizer"
        enum status "UPCOMING|ONGOING|COMPLETED|CANCELLED"
    }
    EVENT_REGISTRATIONS {
        int registration_id PK
        int event_id FK "CASCADE"
        int user_id FK
        enum status "REGISTERED|CANCELLED|ATTENDED"
        datetime registered_at
        datetime attended_at
        unique event_id + user_id "duplicate prevention"
    }
    AUDIT_LOGS {
        bigint audit_id PK
        int user_id FK "nullable"
        varchar action
        varchar entity_type
        int entity_id
        varchar description
        datetime created_at
    }
```

## Relationships (crow's-foot, text form)

```
departments 1 ──── N users                       (fk_users_department, ON DELETE SET NULL)
users       1 ──── N bookings                    (fk_bookings_user,      ON DELETE RESTRICT)
resources   1 ──── N bookings                    (fk_bookings_resource,  ON DELETE RESTRICT)
users       1 ──── N bookings.decided_by         (fk_bookings_decider,   ON DELETE SET NULL)
users       1 ──── N complaints (submitter)      (fk_complaints_user,    ON DELETE RESTRICT)
users       1 ──── N complaints.assigned_to      (fk_complaints_assigned_to, ON DELETE SET NULL)
departments 1 ──── N complaints                  (fk_complaints_department,  ON DELETE SET NULL)
complaints  1 ──── N complaint_updates           (fk_updates_complaint,  ON DELETE CASCADE)
users       1 ──── N complaint_updates           (fk_updates_user,       ON DELETE RESTRICT)
users       1 ──── N events (organizer)          (fk_events_creator,     ON DELETE RESTRICT)
departments 1 ──── N events                      (fk_events_department,  ON DELETE SET NULL)
events      1 ──── N event_registrations         (fk_registrations_event, ON DELETE CASCADE)
users       1 ──── N event_registrations         (fk_registrations_user,  ON DELETE RESTRICT)
users       1 ──── N audit_logs                  (fk_audit_user,         ON DELETE SET NULL)
```

## Design decisions

* **Soft delete where referenced** — users, departments and resources use an
  `is_active`/`status` flag; hard DELETE is only allowed when no child rows exist
  (the service layer enforces this with friendly messages).
* **`ON DELETE RESTRICT`** protects bookings/complaints from orphaning when a user or
  resource is removed; **`CASCADE`** is used only for `complaint_updates` and
  `event_registrations` (history/registrations are meaningless without their parent).
* **`UNIQUE KEY uk_event_user (event_id, user_id)`** gives database-level duplicate
  registration prevention; the insert surfaces MySQL error 1062 as a friendly
  `DuplicateRegistrationException`.
* **`CHECK (start_time < end_time)`** (bookings) and
  **`CHECK (end_time > start_time)`, `CHECK (registration_deadline <= start_time)`**
  (events) add data-level sanity beyond application validation.
* **Index for the hot path** — `idx_bookings_conflict (resource_id, booking_date,
  status, start_time, end_time)` serves the overlap query.
* **audit_logs is append-only** — the DAO exposes no UPDATE/DELETE for it.
