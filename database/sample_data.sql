-- =====================================================================
-- CampusOS - Sample data for demonstration and manual testing.
--
-- Apply AFTER schema.sql:
--   mysql -u <user> -p campusos_db < database/sample_data.sql
--
-- NO ADMIN ACCOUNT IS SEEDED HERE (security requirement).
-- Create the first administrator with the bootstrap tool documented in
-- the README, then manage other admins through the application UI.
--
-- Demo credentials of the seeded accounts (all BCrypt-hashed):
--   Faculty : anita.sharma@campus.edu   password  Faculty@123
--   Faculty : rahul.verma@campus.edu    password  Faculty@123
--   Student : aarav.mehta@student.edu   password  Student@123
--   Student : diya.kapoor@student.edu   password  Student@123
--   Student : kabir.singh@student.edu   password  Student@123
--   Student : meera.iyer@student.edu    password  Student@123
-- =====================================================================

USE campusos_db;

-- ------------------------- departments ------------------------------
INSERT INTO departments (department_name, department_code, description) VALUES
  ('Computer Science & Engineering', 'CSE',  'Programming, AI, systems and software engineering.'),
  ('Electronics & Communication',    'ECE',  'Circuits, embedded systems and communication.'),
  ('Mechanical Engineering',         'MEC',  'Thermal, design and manufacturing disciplines.'),
  ('Civil Engineering',              'CIV',  'Structures, construction and surveying.'),
  ('Business Administration',        'MBA',  'Management, finance and marketing studies.');

-- ------------------------- users ------------------------------------
-- password_hash values are real BCrypt hashes of the demo passwords.
INSERT INTO users (full_name, email, password_hash, role, department_id, identifier, phone) VALUES
  ('Prof. Anita Sharma', 'anita.sharma@campus.edu',
   '$2a$12$9ZbvMqoOHfLhsKBG34BLTOpM0wpev/wqZS5dIfT50LNY9X9DFQIum', 'FACULTY',
   (SELECT department_id FROM departments WHERE department_code = 'CSE'), 'FAC101', '9876500011'),
  ('Prof. Rahul Verma',  'rahul.verma@campus.edu',
   '$2a$12$9ZbvMqoOHfLhsKBG34BLTOpM0wpev/wqZS5dIfT50LNY9X9DFQIum', 'FACULTY',
   (SELECT department_id FROM departments WHERE department_code = 'ECE'), 'FAC102', '9876500022'),
  ('Aarav Mehta', 'aarav.mehta@student.edu',
   '$2a$12$rrO4ymKyoDZR6JUXsPocHepg6JIa5qe0.1vv8.3AGm50Xlkuma7sO', 'STUDENT',
   (SELECT department_id FROM departments WHERE department_code = 'CSE'), 'STU2023CSE01', '9876511001'),
  ('Diya Kapoor', 'diya.kapoor@student.edu',
   '$2a$12$rrO4ymKyoDZR6JUXsPocHepg6JIa5qe0.1vv8.3AGm50Xlkuma7sO', 'STUDENT',
   (SELECT department_id FROM departments WHERE department_code = 'CSE'), 'STU2023CSE02', '9876511002'),
  ('Kabir Singh', 'kabir.singh@student.edu',
   '$2a$12$rrO4ymKyoDZR6JUXsPocHepg6JIa5qe0.1vv8.3AGm50Xlkuma7sO', 'STUDENT',
   (SELECT department_id FROM departments WHERE department_code = 'MEC'), 'STU2023MEC01', '9876511003'),
  ('Meera Iyer',  'meera.iyer@student.edu',
   '$2a$12$rrO4ymKyoDZR6JUXsPocHepg6JIa5qe0.1vv8.3AGm50Xlkuma7sO', 'STUDENT',
   (SELECT department_id FROM departments WHERE department_code = 'MBA'), 'STU2023MBA01', '9876511004');

-- ------------------------- resources --------------------------------
INSERT INTO resources (resource_name, resource_type, location, capacity, description, created_by) VALUES
  ('Computer Lab 1',     'COMPUTER_LAB', 'Block A, Ground Floor',  60,
   '60 systems with i7 processors, dual boot Windows/Linux.',
   (SELECT user_id FROM users WHERE email = 'anita.sharma@campus.edu')),
  ('Computer Lab 2',     'COMPUTER_LAB', 'Block A, First Floor',   40,
   '40 systems configured for networking and embedded labs.',
   (SELECT user_id FROM users WHERE email = 'anita.sharma@campus.edu')),
  ('Classroom A-101',    'CLASSROOM',    'Block A, First Floor',   70, 'Smart classroom with projector.',
   (SELECT user_id FROM users WHERE email = 'rahul.verma@campus.edu')),
  ('Classroom B-204',    'CLASSROOM',    'Block B, Second Floor',  50, 'Standard classroom with whiteboard.',
   (SELECT user_id FROM users WHERE email = 'rahul.verma@campus.edu')),
  ('Seminar Hall (Main)', 'SEMINAR_HALL','Admin Block, Second Floor', 200,
   'Main seminar hall with stage, AC and 200 seats.',
   (SELECT user_id FROM users WHERE email = 'rahul.verma@campus.edu')),
  ('Projector P-01',     'PROJECTOR',    'Media Cell Store',       NULL, 'Portable full-HD projector.',
   (SELECT user_id FROM users WHERE email = 'rahul.verma@campus.edu')),
  ('Projector P-02',     'PROJECTOR',    'Media Cell Store',       NULL, 'Short-throw projector for small rooms.',
   (SELECT user_id FROM users WHERE email = 'anita.sharma@campus.edu')),
  ('Robotics Kit RB-01', 'EQUIPMENT',    'Innovation Lab',         NULL, 'Arduino + sensor kit for workshops.',
   (SELECT user_id FROM users WHERE email = 'anita.sharma@campus.edu'));

-- ------------------------- bookings ---------------------------------
-- A completed booking from yesterday (history),
-- an approved upcoming booking, a pending request awaiting the admin,
-- a rejected request with remarks, and an approved past booking.
INSERT INTO bookings
  (resource_id, user_id, booking_date, start_time, end_time, purpose, status,
   requested_at, decided_at, decided_by, decision_remarks, cancelled_at)
VALUES
  ((SELECT resource_id FROM resources WHERE resource_name = 'Computer Lab 1'),
   (SELECT user_id FROM users WHERE email = 'aarav.mehta@student.edu'),
   DATE_SUB(CURDATE(), INTERVAL 1 DAY), '10:00:00', '12:00:00',
   'Data structures lab practice', 'COMPLETED',
   DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY),
   (SELECT user_id FROM users WHERE email = 'anita.sharma@campus.edu'), NULL, NULL),

  ((SELECT resource_id FROM resources WHERE resource_name = 'Seminar Hall (Main)'),
   (SELECT user_id FROM users WHERE email = 'anita.sharma@campus.edu'),
   DATE_ADD(CURDATE(), INTERVAL 5 DAY), '14:00:00', '17:00:00',
   'Guest lecture on cloud computing', 'APPROVED',
   DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY),
   NULL, NULL, NULL),

  ((SELECT resource_id FROM resources WHERE resource_name = 'Classroom A-101'),
   (SELECT user_id FROM users WHERE email = 'diya.kapoor@student.edu'),
   DATE_ADD(CURDATE(), INTERVAL 2 DAY), '09:00:00', '10:30:00',
   'Coding club practice session', 'PENDING',
   NOW(), NULL, NULL, NULL, NULL),

  ((SELECT resource_id FROM resources WHERE resource_name = 'Projector P-01'),
   (SELECT user_id FROM users WHERE email = 'kabir.singh@student.edu'),
   DATE_ADD(CURDATE(), INTERVAL 1 DAY), '15:00:00', '16:30:00',
   'Robotics club presentation', 'REJECTED',
   DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(),
   NULL, 'Projector reserved for official placement drive on that day.', NULL),

  ((SELECT resource_id FROM resources WHERE resource_name = 'Classroom B-204'),
   (SELECT user_id FROM users WHERE email = 'rahul.verma@campus.edu'),
   DATE_SUB(CURDATE(), INTERVAL 7 DAY), '11:00:00', '13:00:00',
   'VLSI design workshop', 'COMPLETED',
   DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 9 DAY),
   NULL, NULL, NULL);

-- ------------------------- complaints -------------------------------
INSERT INTO complaints
  (user_id, department_id, assigned_to, category, title, description, priority, status,
   created_at, updated_at, resolved_at)
VALUES
  ((SELECT user_id FROM users WHERE email = 'aarav.mehta@student.edu'),
   (SELECT department_id FROM departments WHERE department_code = 'CSE'),
   NULL, 'INFRASTRUCTURE',
   'Broken fan in Classroom A-101',
   'The ceiling fan in classroom A-101 makes a loud noise and stops after a few minutes. Classes in the afternoon slot are affected.',
   'HIGH', 'SUBMITTED', NOW(), NOW(), NULL),

  ((SELECT user_id FROM users WHERE email = 'diya.kapoor@student.edu'),
   (SELECT department_id FROM departments WHERE department_code = 'CSE'),
   (SELECT user_id FROM users WHERE email = 'anita.sharma@campus.edu'),
   'IT_SUPPORT',
   'Lab computers missing Java IDE',
   'Computer Lab 2 systems 12-18 do not have any Java IDE installed. We cannot complete the OOP lab assignment during the scheduled slot.',
   'MEDIUM', 'IN_PROGRESS',
   DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL),

  ((SELECT user_id FROM users WHERE email = 'meera.iyer@student.edu'),
   (SELECT department_id FROM departments WHERE department_code = 'MBA'),
   (SELECT user_id FROM users WHERE email = 'rahul.verma@campus.edu'),
   'LIBRARY',
   'Library database access expired',
   'Access to the IEEE digital library shows an expired subscription error for MBA students.',
   'LOW', 'RESOLVED',
   DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY),
   DATE_SUB(NOW(), INTERVAL 4 DAY));

-- Complaint status history (append-only)
INSERT INTO complaint_updates (complaint_id, updated_by, previous_status, new_status, remarks, created_at) VALUES
  ((SELECT complaint_id FROM complaints WHERE title = 'Lab computers missing Java IDE'),
   (SELECT user_id FROM users WHERE email = 'anita.sharma@campus.edu'),
   'SUBMITTED', 'ASSIGNED',
   'Assigned to Prof. Anita Sharma (CSE). Installed on systems 12-15 in the first batch.',
   DATE_SUB(NOW(), INTERVAL 2 DAY)),
  ((SELECT complaint_id FROM complaints WHERE title = 'Lab computers missing Java IDE'),
   (SELECT user_id FROM users WHERE email = 'anita.sharma@campus.edu'),
   'ASSIGNED', 'IN_PROGRESS',
   'Remaining systems being configured; lab images are being updated.',
   DATE_SUB(NOW(), INTERVAL 1 DAY)),
  ((SELECT complaint_id FROM complaints WHERE title = 'Library database access expired'),
   (SELECT user_id FROM users WHERE email = 'rahul.verma@campus.edu'),
   'SUBMITTED', 'ASSIGNED',
   'Coordinating with the library office regarding the renewal.',
   DATE_SUB(NOW(), INTERVAL 6 DAY)),
  ((SELECT complaint_id FROM complaints WHERE title = 'Library database access expired'),
   (SELECT user_id FROM users WHERE email = 'rahul.verma@campus.edu'),
   'ASSIGNED', 'RESOLVED',
   'Subscription renewed with the vendor; access restored for all departments.',
   DATE_SUB(NOW(), INTERVAL 4 DAY));

-- ------------------------- events -----------------------------------
INSERT INTO events
  (title, description, venue, start_time, end_time, registration_deadline,
   max_participants, created_by, department_id, status)
VALUES
  ('Annual Tech Fest 2026', 'Two-day technical festival: hackathon, coding contest, robotics showcase and tech talks.',
   'Seminar Hall (Main)',
   DATE_ADD(NOW(), INTERVAL 14 DAY), DATE_ADD(NOW(), INTERVAL 15 DAY),
   DATE_ADD(NOW(), INTERVAL 10 DAY), 150,
   (SELECT user_id FROM users WHERE email = 'anita.sharma@campus.edu'),
   (SELECT department_id FROM departments WHERE department_code = 'CSE'), 'UPCOMING'),

  ('Placement Readiness Workshop', 'Mock aptitude tests, resume review and group discussion practice.',
   'Classroom B-204',
   DATE_ADD(NOW(), INTERVAL 6 DAY), DATE_ADD(NOW(), INTERVAL 6 DAY) + INTERVAL 4 HOUR,
   DATE_ADD(NOW(), INTERVAL 4 DAY), 40,
   (SELECT user_id FROM users WHERE email = 'rahul.verma@campus.edu'),
   (SELECT department_id FROM departments WHERE department_code = 'MBA'), 'UPCOMING'),

  ('IoT Hands-on Bootcamp', 'Introduction to Arduino, sensors and cloud dashboards.',
   'Computer Lab 2',
   DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY) + INTERVAL 6 HOUR,
   DATE_SUB(NOW(), INTERVAL 12 DAY), 30,
   (SELECT user_id FROM users WHERE email = 'anita.sharma@campus.edu'),
   (SELECT department_id FROM departments WHERE department_code = 'ECE'), 'COMPLETED');

-- ------------------------- event registrations ----------------------
INSERT INTO event_registrations (event_id, user_id, status, registered_at, attended_at) VALUES
  ((SELECT event_id FROM events WHERE title = 'Annual Tech Fest 2026'),
   (SELECT user_id FROM users WHERE email = 'aarav.mehta@student.edu'), 'REGISTERED', NOW(), NULL),
  ((SELECT event_id FROM events WHERE title = 'Annual Tech Fest 2026'),
   (SELECT user_id FROM users WHERE email = 'diya.kapoor@student.edu'), 'REGISTERED', NOW(), NULL),
  ((SELECT event_id FROM events WHERE title = 'Placement Readiness Workshop'),
   (SELECT user_id FROM users WHERE email = 'meera.iyer@student.edu'), 'REGISTERED', NOW(), NULL),
  ((SELECT event_id FROM events WHERE title = 'Placement Readiness Workshop'),
   (SELECT user_id FROM users WHERE email = 'kabir.singh@student.edu'), 'REGISTERED', NOW(), NULL),
  ((SELECT event_id FROM events WHERE title = 'IoT Hands-on Bootcamp'),
   (SELECT user_id FROM users WHERE email = 'aarav.mehta@student.edu'), 'ATTENDED',
   DATE_SUB(NOW(), INTERVAL 13 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY)),
  ((SELECT event_id FROM events WHERE title = 'IoT Hands-on Bootcamp'),
   (SELECT user_id FROM users WHERE email = 'kabir.singh@student.edu'), 'ATTENDED',
   DATE_SUB(NOW(), INTERVAL 13 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY)),
  ((SELECT event_id FROM events WHERE title = 'IoT Hands-on Bootcamp'),
   (SELECT user_id FROM users WHERE email = 'diya.kapoor@student.edu'), 'CANCELLED',
   DATE_SUB(NOW(), INTERVAL 13 DAY), NULL);

-- Cancelled registrations keep cancelled_at (seat released back to capacity)
UPDATE event_registrations
   SET cancelled_at = DATE_SUB(NOW(), INTERVAL 11 DAY)
 WHERE status = 'CANCELLED';

-- ------------------------- audit logs -------------------------------
INSERT INTO audit_logs (user_id, action, entity_type, entity_id, description, created_at) VALUES
  ((SELECT user_id FROM users WHERE email = 'aarav.mehta@student.edu'),
   'BOOKING_REQUESTED', 'BOOKING', 3, 'Booking requested for resource #3 (Classroom A-101).', NOW()),
  ((SELECT user_id FROM users WHERE email = 'diya.kapoor@student.edu'),
   'COMPLAINT_SUBMITTED', 'COMPLAINT', 2, 'Complaint submitted: Lab computers missing Java IDE.',
   DATE_SUB(NOW(), INTERVAL 3 DAY)),
  ((SELECT user_id FROM users WHERE email = 'aarav.mehta@student.edu'),
   'EVENT_REGISTERED', 'EVENT', 1, 'Aarav Mehta registered for event Annual Tech Fest 2026.', NOW()),
  ((SELECT user_id FROM users WHERE email = 'kabir.singh@student.edu'),
   'BOOKING_REQUESTED', 'BOOKING', 4, 'Booking requested for resource #6 (Projector P-01).',
   DATE_SUB(NOW(), INTERVAL 1 DAY));

-- =====================================================================
-- Sample data load complete. Log in as seeded faculty/student accounts,
-- bootstrap an administrator, and explore all five modules.
-- =====================================================================
