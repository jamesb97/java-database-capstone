# Schema Design

## MySQL Database Design

The MySQL database stores the structured, relational core of the clinic system: patients, doctors, appointments, admins, clinic locations, and payments. These entities have well-defined relationships and benefit from referential integrity, so a relational model is the right fit.

Before the tables, here are the key design decisions and the reasoning behind them:

- **What happens when a patient is deleted?** We do **not** hard-delete patients in practice, because appointments and payments are part of the clinic's medical and financial record and may be legally required to be retained. The recommended approach is a **soft delete** (an `is_active` / `deleted_at` flag) so history is preserved. At the database level, the `appointments.patient_id` foreign key uses `ON DELETE RESTRICT`, which prevents a patient row from being physically removed while appointments still reference it. If a hard delete is ever truly required, the application layer must first archive or remove the dependent rows.
- **Should appointments be deleted when a patient is deleted?** No. Cascading the deletion of appointments would destroy historical/medical data. We keep appointments and rely on soft deletes for patients. (If the requirement were the opposite — e.g., a temporary/guest patient — `ON DELETE CASCADE` could be used instead, but that is not appropriate for a medical record.)
- **Should a doctor be allowed to overlapping appointments?** No. A doctor cannot be in two appointments at the same time. MySQL cannot directly express "no overlapping time ranges" with a single constraint. We enforce this in two layers: (1) a `UNIQUE` constraint on `(doctor_id, appointment_time)` to block exact duplicate start times, and (2) **application/service-layer validation** that checks for any overlap between the requested `[appointment_time, end_time)` window and existing appointments for that doctor before saving.
- **Should we validate email or phone formats?** Yes, but primarily in **application code** (e.g., Java Bean Validation `@Email`, `@Pattern`). The database enforces structural rules (`NOT NULL`, `UNIQUE`, length limits) and optionally a `CHECK` constraint as a safety net, but full format validation is cleaner and gives better error messages at the application layer.
- **General conventions:** Every table has an `AUTO_INCREMENT` surrogate primary key (`BIGINT`). Timestamps (`created_at`, `updated_at`) are included for auditing. `ENUM` is used for small fixed sets of values (status, role).

---

### Table: patients

| Column        | Type                     | Constraints                                       | Notes                                  |
|---------------|--------------------------|---------------------------------------------------|----------------------------------------|
| id            | BIGINT                   | PRIMARY KEY, AUTO_INCREMENT                       | Surrogate key                          |
| first_name    | VARCHAR(50)              | NOT NULL                                          |                                        |
| last_name     | VARCHAR(50)              | NOT NULL                                          |                                        |
| email         | VARCHAR(100)             | NOT NULL, UNIQUE                                  | Used for login; validated in code      |
| password_hash | VARCHAR(255)             | NOT NULL                                          | Never store plain-text passwords       |
| phone         | VARCHAR(20)              | NOT NULL                                          | Format validated in code               |
| date_of_birth | DATE                     | NULL                                              |                                        |
| gender        | ENUM('M','F','OTHER')    | NULL                                              |                                        |
| address       | VARCHAR(255)             | NULL                                              |                                        |
| is_active     | BOOLEAN                  | NOT NULL DEFAULT TRUE                             | Supports soft delete                   |
| created_at    | TIMESTAMP                | NOT NULL DEFAULT CURRENT_TIMESTAMP                |                                        |
| updated_at    | TIMESTAMP                | NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP |                            |

---

### Table: doctors

| Column         | Type                  | Constraints                                       | Notes                                       |
|----------------|-----------------------|---------------------------------------------------|---------------------------------------------|
| id             | BIGINT                | PRIMARY KEY, AUTO_INCREMENT                       | Surrogate key                               |
| first_name     | VARCHAR(50)           | NOT NULL                                          |                                             |
| last_name      | VARCHAR(50)           | NOT NULL                                          |                                             |
| email          | VARCHAR(100)          | NOT NULL, UNIQUE                                  | Used for login; validated in code           |
| password_hash  | VARCHAR(255)          | NOT NULL                                          |                                             |
| phone          | VARCHAR(20)           | NOT NULL                                          | Format validated in code                    |
| specialization | VARCHAR(100)          | NOT NULL                                          | e.g., Cardiology, Dermatology               |
| clinic_id      | BIGINT                | NULL, FOREIGN KEY → clinic_locations(id)          | Primary clinic; ON DELETE SET NULL          |
| is_active      | BOOLEAN               | NOT NULL DEFAULT TRUE                             | Soft delete / availability for new bookings |
| created_at     | TIMESTAMP             | NOT NULL DEFAULT CURRENT_TIMESTAMP                |                                             |
| updated_at     | TIMESTAMP             | NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP |                                 |

---

### Table: appointments

| Column           | Type                                              | Constraints                                                  | Notes                                          |
|------------------|---------------------------------------------------|--------------------------------------------------------------|------------------------------------------------|
| id               | BIGINT                                            | PRIMARY KEY, AUTO_INCREMENT                                  | Surrogate key                                  |
| patient_id       | BIGINT                                            | NOT NULL, FOREIGN KEY → patients(id) ON DELETE RESTRICT      | Keep history; block patient delete             |
| doctor_id        | BIGINT                                            | NOT NULL, FOREIGN KEY → doctors(id) ON DELETE RESTRICT       | Keep history; block doctor delete              |
| clinic_id        | BIGINT                                            | NULL, FOREIGN KEY → clinic_locations(id) ON DELETE SET NULL  | Where the appointment occurs                   |
| appointment_time | DATETIME                                          | NOT NULL                                                     | Start time                                     |
| end_time         | DATETIME                                          | NOT NULL                                                     | Default 1 hour after start                     |
| status           | ENUM('SCHEDULED','COMPLETED','CANCELLED','NO_SHOW') | NOT NULL DEFAULT 'SCHEDULED'                               |                                                |
| reason           | VARCHAR(255)                                      | NULL                                                         | Reason for visit                               |
| created_at       | TIMESTAMP                                         | NOT NULL DEFAULT CURRENT_TIMESTAMP                          |                                                |
| updated_at       | TIMESTAMP                                         | NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP |                                              |

**Constraints / indexes:**

- `UNIQUE (doctor_id, appointment_time)` — prevents two appointments for the same doctor at the same start time. Overlap beyond exact start times is enforced in the service layer.
- `INDEX (patient_id)`, `INDEX (doctor_id, appointment_time)` — speeds up calendar and "my appointments" queries.
- `CHECK (end_time > appointment_time)` — guards against invalid time ranges.

---

### Table: admin

| Column        | Type         | Constraints                                       | Notes                              |
|---------------|--------------|---------------------------------------------------|------------------------------------|
| id            | BIGINT       | PRIMARY KEY, AUTO_INCREMENT                       | Surrogate key                      |
| username      | VARCHAR(50)  | NOT NULL, UNIQUE                                  | Login name                         |
| email         | VARCHAR(100) | NOT NULL, UNIQUE                                  | Validated in code                  |
| password_hash | VARCHAR(255) | NOT NULL                                          |                                    |
| role          | ENUM('SUPER_ADMIN','ADMIN') | NOT NULL DEFAULT 'ADMIN'           | Future-proofing for access levels  |
| created_at    | TIMESTAMP    | NOT NULL DEFAULT CURRENT_TIMESTAMP                |                                    |
| updated_at    | TIMESTAMP    | NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP |                        |

---

### Table: clinic_locations

| Column     | Type         | Constraints                                       | Notes                |
|------------|--------------|---------------------------------------------------|----------------------|
| id         | BIGINT       | PRIMARY KEY, AUTO_INCREMENT                       | Surrogate key        |
| name       | VARCHAR(100) | NOT NULL                                          | Branch name          |
| address    | VARCHAR(255) | NOT NULL                                          |                      |
| phone      | VARCHAR(20)  | NULL                                              | Validated in code    |
| created_at | TIMESTAMP    | NOT NULL DEFAULT CURRENT_TIMESTAMP                |                      |
| updated_at | TIMESTAMP    | NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP |          |

---

### Table: payments

| Column         | Type                                          | Constraints                                                   | Notes                                  |
|----------------|-----------------------------------------------|---------------------------------------------------------------|----------------------------------------|
| id             | BIGINT                                        | PRIMARY KEY, AUTO_INCREMENT                                   | Surrogate key                          |
| appointment_id | BIGINT                                        | NOT NULL, FOREIGN KEY → appointments(id) ON DELETE RESTRICT  | One payment per appointment            |
| patient_id     | BIGINT                                        | NOT NULL, FOREIGN KEY → patients(id) ON DELETE RESTRICT      | Denormalized for fast patient billing  |
| amount         | DECIMAL(10,2)                                 | NOT NULL                                                      | Use DECIMAL for money, never FLOAT     |
| status         | ENUM('PENDING','PAID','REFUNDED','FAILED')    | NOT NULL DEFAULT 'PENDING'                                   |                                        |
| method         | ENUM('CARD','CASH','INSURANCE')               | NULL                                                         |                                        |
| paid_at        | TIMESTAMP                                     | NULL                                                         | Set when status becomes PAID           |
| created_at     | TIMESTAMP                                     | NOT NULL DEFAULT CURRENT_TIMESTAMP                          |                                        |
| updated_at     | TIMESTAMP                                     | NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP |                                      |

**Constraints:**

- `CHECK (amount >= 0)` — payments cannot be negative.
- `INDEX (patient_id)` — for retrieving a patient's billing history.

---

### Relationship summary

- A **patient** can have many **appointments** (1-to-many).
- A **doctor** can have many **appointments** (1-to-many).
- A **clinic_location** can host many **doctors** and many **appointments** (1-to-many).
- An **appointment** has at most one **payment** (1-to-1, optional).
- **Deletion policy:** patients and doctors use soft deletes (`is_active`); their appointments are protected by `ON DELETE RESTRICT` so historical and financial records are never lost.

---

### SQL DDL

The complete `CREATE TABLE` statements for the design above. Tables are created in dependency order (referenced tables first).

```sql
CREATE TABLE clinic_locations (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    address     VARCHAR(255) NOT NULL,
    phone       VARCHAR(20),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE TABLE patients (
    id            BIGINT                 NOT NULL AUTO_INCREMENT,
    first_name    VARCHAR(50)            NOT NULL,
    last_name     VARCHAR(50)            NOT NULL,
    email         VARCHAR(100)           NOT NULL,
    password_hash VARCHAR(255)           NOT NULL,
    phone         VARCHAR(20)            NOT NULL,
    date_of_birth DATE,
    gender        ENUM('M','F','OTHER'),
    address       VARCHAR(255),
    is_active     BOOLEAN                NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP              NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP              NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_patients_email (email)
) ENGINE = InnoDB;

CREATE TABLE doctors (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    first_name     VARCHAR(50)  NOT NULL,
    last_name      VARCHAR(50)  NOT NULL,
    email          VARCHAR(100) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    phone          VARCHAR(20)  NOT NULL,
    specialization VARCHAR(100) NOT NULL,
    clinic_id      BIGINT,
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_doctors_email (email),
    CONSTRAINT fk_doctors_clinic
        FOREIGN KEY (clinic_id) REFERENCES clinic_locations (id)
        ON DELETE SET NULL
) ENGINE = InnoDB;

CREATE TABLE admin (
    id            BIGINT                       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(50)                  NOT NULL,
    email         VARCHAR(100)                 NOT NULL,
    password_hash VARCHAR(255)                 NOT NULL,
    role          ENUM('SUPER_ADMIN','ADMIN')  NOT NULL DEFAULT 'ADMIN',
    created_at    TIMESTAMP                    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP                    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_admin_username (username),
    UNIQUE KEY uq_admin_email (email)
) ENGINE = InnoDB;

CREATE TABLE appointments (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    patient_id       BIGINT       NOT NULL,
    doctor_id        BIGINT       NOT NULL,
    clinic_id        BIGINT,
    appointment_time DATETIME     NOT NULL,
    end_time         DATETIME     NOT NULL,
    status           ENUM('SCHEDULED','COMPLETED','CANCELLED','NO_SHOW')
                                  NOT NULL DEFAULT 'SCHEDULED',
    reason           VARCHAR(255),
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- Blocks exact double-booking; full overlap checks happen in the service layer
    UNIQUE KEY uq_doctor_slot (doctor_id, appointment_time),
    KEY idx_appointments_patient (patient_id),
    KEY idx_appointments_doctor_time (doctor_id, appointment_time),
    CONSTRAINT chk_appointment_window CHECK (end_time > appointment_time),
    CONSTRAINT fk_appointments_patient
        FOREIGN KEY (patient_id) REFERENCES patients (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_appointments_doctor
        FOREIGN KEY (doctor_id) REFERENCES doctors (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_appointments_clinic
        FOREIGN KEY (clinic_id) REFERENCES clinic_locations (id)
        ON DELETE SET NULL
) ENGINE = InnoDB;

CREATE TABLE payments (
    id             BIGINT                                      NOT NULL AUTO_INCREMENT,
    appointment_id BIGINT                                      NOT NULL,
    patient_id     BIGINT                                      NOT NULL,
    amount         DECIMAL(10,2)                               NOT NULL,
    status         ENUM('PENDING','PAID','REFUNDED','FAILED')  NOT NULL DEFAULT 'PENDING',
    method         ENUM('CARD','CASH','INSURANCE'),
    paid_at        TIMESTAMP NULL,
    created_at     TIMESTAMP                                   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP                                   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_payments_appointment (appointment_id),
    KEY idx_payments_patient (patient_id),
    CONSTRAINT chk_payment_amount CHECK (amount >= 0),
    CONSTRAINT fk_payments_appointment
        FOREIGN KEY (appointment_id) REFERENCES appointments (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_payments_patient
        FOREIGN KEY (patient_id) REFERENCES patients (id)
        ON DELETE RESTRICT
) ENGINE = InnoDB;
```

## MongoDB Collection Design
