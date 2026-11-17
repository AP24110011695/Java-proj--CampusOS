-- =====================================================================
-- CampusOS - Smart Campus Management System
-- Complete MySQL schema (normalized, InnoDB, referential integrity)
--
-- Apply with:  mysql -u <user> -p < database/schema.sql
-- (Requires a MySQL account with CREATE privileges, e.g. root.)
-- =====================================================================

CREATE DATABASE IF NOT EXISTS campusos_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE campusos_db;

-- Drop in reverse dependency order so the script is re-runnable.
DROP TABLE IF EXISTS audit_logs;
DROP TABLE IF EXISTS event_registrations;
DROP TABLE IF EXISTS events;
DROP TABLE IF EXISTS complaint_updates;
DROP TABLE IF EXISTS complaints;
DROP TABLE IF EXISTS bookings;
DROP TABLE IF EXISTS resources;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS departments;

-- ---------------------------------------------------------------------
-- 1. departments
-- ---------------------------------------------------------------------
CREATE TABLE departments (
  department_id   INT UNSIGNED     NOT NULL AUTO_INCREMENT,
  department_name VARCHAR(100)     NOT NULL,
  department_code VARCHAR(10)      NOT NULL,
  description     VARCHAR(255)     NULL,
  is_active       TINYINT(1)       NOT NULL DEFAULT 1,
  created_at      DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP
                                   ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (department_id),
  UNIQUE KEY uk_department_name (department_name),
  UNIQUE KEY uk_department_code (department_code)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 2. users  (one department -> many users)
-- ---------------------------------------------------------------------
CREATE TABLE users (
  user_id       INT UNSIGNED   NOT NULL AUTO_INCREMENT,
  full_name     VARCHAR(100)   NOT NULL,
  email         VARCHAR(120)   NOT NULL,
  password_hash VARCHAR(72)    NOT NULL COMMENT 'BCrypt hash - plain-text passwords are never stored',
  role          ENUM('ADMIN','FACULTY','STUDENT') NOT NULL,
  department_id INT UNSIGNED   NULL,
  identifier    VARCHAR(20)    NULL COMMENT 'student roll number / employee ID',
  phone         VARCHAR(15)    NULL,
  is_active     TINYINT(1)     NOT NULL DEFAULT 1 COMMENT 'soft delete: deactivate instead of delete',
  created_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP
                               ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  UNIQUE KEY uk_user_email (email),
  UNIQUE KEY uk_user_identifier (identifier),
  CONSTRAINT fk_users_department
    FOREIGN KEY (department_id) REFERENCES departments (department_id)
    ON DELETE SET NULL ON UPDATE CASCADE,
  INDEX idx_users_role (role),
  INDEX idx_users_department (department_id),
  INDEX idx_users_active (is_active)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 3. resources  (one user/admin -> many resources)
-- ---------------------------------------------------------------------
CREATE TABLE resources (
  resource_id   INT UNSIGNED   NOT NULL AUTO_INCREMENT,
  resource_name VARCHAR(100)   NOT NULL,
  resource_type ENUM('CLASSROOM','COMPUTER_LAB','SEMINAR_HALL','PROJECTOR','EQUIPMENT','OTHER') NOT NULL,
  location      VARCHAR(100)   NULL,
  capacity      INT UNSIGNED   NULL,
  description   VARCHAR(255)   NULL,
  status        ENUM('ACTIVE','INACTIVE','MAINTENANCE') NOT NULL DEFAULT 'ACTIVE',
  created_by    INT UNSIGNED   NULL,
  created_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP
                               ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (resource_id),
  CONSTRAINT fk_resources_creator
    FOREIGN KEY (created_by) REFERENCES users (user_id)
    ON DELETE SET NULL ON UPDATE CASCADE,
  INDEX idx_resources_type (resource_type),
  INDEX idx_resources_status (status)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 4. bookings  (many users -> many resources through bookings)
--    Conflict rule: newStart < existingEnd AND newEnd > existingStart
--    against PENDING/APPROVED bookings, enforced inside a transaction.
-- ---------------------------------------------------------------------
CREATE TABLE bookings (
  booking_id       INT UNSIGNED  NOT NULL AUTO_INCREMENT,
  resource_id      INT UNSIGNED  NOT NULL,
  user_id          INT UNSIGNED  NOT NULL,
  booking_date     DATE          NOT NULL,
  start_time       TIME          NOT NULL,
  end_time         TIME          NOT NULL,
  purpose          VARCHAR(255)  NOT NULL,
  status           ENUM('PENDING','APPROVED','REJECTED','CANCELLED','COMPLETED') NOT NULL DEFAULT 'PENDING',
  requested_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  decided_at       DATETIME      NULL,
  decided_by       INT UNSIGNED  NULL,
  decision_remarks VARCHAR(255)  NULL,
  cancelled_at     DATETIME      NULL,
  PRIMARY KEY (booking_id),
  CONSTRAINT fk_bookings_resource
    FOREIGN KEY (resource_id) REFERENCES resources (resource_id)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT fk_bookings_user
    FOREIGN KEY (user_id) REFERENCES users (user_id)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT fk_bookings_decider
    FOREIGN KEY (decided_by) REFERENCES users (user_id)
    ON DELETE SET NULL ON UPDATE CASCADE,
  -- covering index for the overlap-detection query
  INDEX idx_bookings_conflict (resource_id, booking_date, status, start_time, end_time),
  INDEX idx_bookings_user (user_id),
  INDEX idx_bookings_date (booking_date),
  CONSTRAINT chk_booking_time_order CHECK (start_time < end_time)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 5. complaints  (one user -> many complaints, assigned to faculty)
-- ---------------------------------------------------------------------
CREATE TABLE complaints (
  complaint_id  INT UNSIGNED  NOT NULL AUTO_INCREMENT,
  user_id       INT UNSIGNED  NOT NULL COMMENT 'submitter',
  department_id INT UNSIGNED  NULL COMMENT 'department the complaint relates to',
  assigned_to   INT UNSIGNED  NULL COMMENT 'faculty member handling the complaint',
  category      ENUM('INFRASTRUCTURE','ACADEMIC','HOSTEL','IT_SUPPORT','TRANSPORT','LIBRARY','OTHER') NOT NULL,
  title         VARCHAR(150)  NOT NULL,
  description   TEXT          NOT NULL,
  priority      ENUM('LOW','MEDIUM','HIGH','CRITICAL') NOT NULL DEFAULT 'MEDIUM',
  status        ENUM('SUBMITTED','ASSIGNED','IN_PROGRESS','RESOLVED','CLOSED') NOT NULL DEFAULT 'SUBMITTED',
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
                              ON UPDATE CURRENT_TIMESTAMP,
  resolved_at   DATETIME      NULL,
  PRIMARY KEY (complaint_id),
  CONSTRAINT fk_complaints_user
    FOREIGN KEY (user_id) REFERENCES users (user_id)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT fk_complaints_department
    FOREIGN KEY (department_id) REFERENCES departments (department_id)
    ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT fk_complaints_assigned_to
    FOREIGN KEY (assigned_to) REFERENCES users (user_id)
    ON DELETE SET NULL ON UPDATE CASCADE,
  INDEX idx_complaints_status (status),
  INDEX idx_complaints_assigned (assigned_to),
  INDEX idx_complaints_department (department_id),
  INDEX idx_complaints_category (category)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 6. complaint_updates  (append-only history - one complaint -> many updates)
-- ---------------------------------------------------------------------
CREATE TABLE complaint_updates (
  update_id       INT UNSIGNED NOT NULL AUTO_INCREMENT,
  complaint_id    INT UNSIGNED NOT NULL,
  updated_by      INT UNSIGNED NOT NULL,
  previous_status ENUM('SUBMITTED','ASSIGNED','IN_PROGRESS','RESOLVED','CLOSED') NULL,
  new_status      ENUM('SUBMITTED','ASSIGNED','IN_PROGRESS','RESOLVED','CLOSED') NOT NULL,
  remarks         VARCHAR(500) NOT NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (update_id),
  CONSTRAINT fk_updates_complaint
    FOREIGN KEY (complaint_id) REFERENCES complaints (complaint_id)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_updates_user
    FOREIGN KEY (updated_by) REFERENCES users (user_id)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  INDEX idx_updates_complaint (complaint_id, created_at)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 7. events  (organizer -> events)
-- ---------------------------------------------------------------------
CREATE TABLE events (
  event_id               INT UNSIGNED  NOT NULL AUTO_INCREMENT,
  title                  VARCHAR(150)  NOT NULL,
  description            TEXT          NULL,
  venue                  VARCHAR(120)  NOT NULL,
  start_time             DATETIME      NOT NULL,
  end_time               DATETIME      NOT NULL,
  registration_deadline  DATETIME      NOT NULL,
  max_participants       INT UNSIGNED  NOT NULL,
  created_by             INT UNSIGNED  NOT NULL COMMENT 'organizer (admin or permitted faculty)',
  department_id          INT UNSIGNED  NULL,
  status                 ENUM('UPCOMING','ONGOING','COMPLETED','CANCELLED') NOT NULL DEFAULT 'UPCOMING',
  created_at             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
                                       ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (event_id),
  CONSTRAINT fk_events_creator
    FOREIGN KEY (created_by) REFERENCES users (user_id)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT fk_events_department
    FOREIGN KEY (department_id) REFERENCES departments (department_id)
    ON DELETE SET NULL ON UPDATE CASCADE,
  INDEX idx_events_status (status),
  INDEX idx_events_start (start_time),
  INDEX idx_events_creator (created_by),
  CONSTRAINT chk_event_time_order CHECK (end_time > start_time),
  CONSTRAINT chk_event_deadline CHECK (registration_deadline <= start_time)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 8. event_registrations
--    (user_id, event_id) unique: one registration per user per event.
-- ---------------------------------------------------------------------
CREATE TABLE event_registrations (
  registration_id INT UNSIGNED NOT NULL AUTO_INCREMENT,
  event_id        INT UNSIGNED NOT NULL,
  user_id         INT UNSIGNED NOT NULL,
  status          ENUM('REGISTERED','CANCELLED','ATTENDED') NOT NULL DEFAULT 'REGISTERED',
  registered_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  cancelled_at    DATETIME     NULL,
  attended_at     DATETIME     NULL,
  PRIMARY KEY (registration_id),
  UNIQUE KEY uk_event_user (event_id, user_id),
  CONSTRAINT fk_registrations_event
    FOREIGN KEY (event_id) REFERENCES events (event_id)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_registrations_user
    FOREIGN KEY (user_id) REFERENCES users (user_id)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  INDEX idx_registrations_user (user_id),
  INDEX idx_registrations_status (status)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 9. audit_logs  (append-only, admin-visible)
-- ---------------------------------------------------------------------
CREATE TABLE audit_logs (
  audit_id    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id     INT UNSIGNED    NULL COMMENT 'who performed the action (NULL = system)',
  action      VARCHAR(60)     NOT NULL,
  entity_type VARCHAR(40)     NOT NULL,
  entity_id   INT UNSIGNED    NULL,
  description VARCHAR(500)    NOT NULL,
  created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (audit_id),
  CONSTRAINT fk_audit_user
    FOREIGN KEY (user_id) REFERENCES users (user_id)
    ON DELETE SET NULL ON UPDATE CASCADE,
  INDEX idx_audit_action (action),
  INDEX idx_audit_time (created_at),
  INDEX idx_audit_entity (entity_type, entity_id)
) ENGINE = InnoDB;

-- =====================================================================
-- Schema creation complete. Run sample_data.sql next for demo content.
-- The FIRST ADMIN ACCOUNT is never inserted here: create it with the
-- secure bootstrap tool documented in the README.
-- =====================================================================
