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

MongoDB complements the MySQL schema by storing data that does not fit well into rigid tables: free-form notes, nested structures, optional fields that vary from record to record, and metadata that evolves over time. For this system, the primary collection is **`prescriptions`** — the structured "who/when" of a visit lives in MySQL (`appointments`), while the flexible "what was prescribed and why" lives in MongoDB.

### Why prescriptions fit MongoDB

- A prescription can have **one or many medications**, each with its own dosage, schedule, and instructions — an array of embedded documents models this naturally, where MySQL would need extra join tables.
- Doctors attach **free-form notes, tags, and file attachments** (e.g., a scanned lab result) that vary per prescription.
- Pharmacies and refill rules differ by medication and region, so the shape of the data is **not uniform** across records.

### Design decisions

- **Reference IDs, not full objects.** The document stores `patientId`, `doctorId`, and `appointmentId` referencing the MySQL rows — never a full embedded copy of the patient. If the full patient object were embedded, every change to the patient's phone or address would have to be propagated into every prescription document (or worse, left stale). The one exception: we **denormalize a small snapshot** (`patientName`, `doctorName`) purely for display and audit purposes, because a prescription is a point-in-time record — the name *as it was when prescribed* is actually the correct value to keep, even if the patient later changes their name.
- **Arrays and embedded documents where they make sense.** `medications` is an array of embedded documents (a prescription is meaningless without them, and they are never queried independently). `refills` is an embedded history array. `attachments` holds file metadata, with the binary itself in object storage — not in the document.
- **`tags` and `metadata`** support search/filtering (`tags`) and operational context (`metadata`: which pharmacy system it was sent to, the client app version, etc.) without schema changes.
- **Schema evolution.** Documents carry a `schemaVersion` field. Because MongoDB does not enforce a uniform schema, new fields (e.g., adding `insurance` next quarter) can be introduced without migrating old documents — readers treat missing fields as "not present" and the application upgrades documents lazily on write. This is the key advantage over the MySQL side, where the same change would require an `ALTER TABLE`.

### Example document: `prescriptions`

```json
{
  "_id": { "$oid": "665f1c2ab9e8a3d4f0a12345" },
  "schemaVersion": 2,

  "appointmentId": 51,
  "patientId": 17,
  "patientName": "Maria Petrova",
  "doctorId": 4,
  "doctorName": "Dr. Ivan Georgiev",

  "issuedAt": { "$date": "2026-06-11T14:30:00Z" },
  "validUntil": { "$date": "2026-09-11T00:00:00Z" },
  "status": "ACTIVE",

  "medications": [
    {
      "name": "Amoxicillin",
      "dosage": "500mg",
      "form": "capsule",
      "frequency": "3 times daily",
      "durationDays": 7,
      "instructions": "Take with food. Complete the full course even if symptoms improve.",
      "refillsAllowed": 0
    },
    {
      "name": "Ibuprofen",
      "dosage": "200mg",
      "form": "tablet",
      "frequency": "as needed, max 3 per day",
      "durationDays": 5,
      "instructions": "Do not take on an empty stomach.",
      "refillsAllowed": 1
    }
  ],

  "doctorNotes": "Patient reports penicillin tolerance confirmed in 2024. Follow up if fever persists beyond 72 hours.",

  "refills": [
    {
      "medication": "Ibuprofen",
      "requestedAt": { "$date": "2026-06-18T09:12:00Z" },
      "approvedBy": 4,
      "status": "APPROVED"
    }
  ],

  "pharmacy": {
    "name": "City Pharmacy #12",
    "address": "45 Vitosha Blvd, Sofia",
    "phone": "0888123456"
  },

  "attachments": [
    {
      "fileName": "throat-culture-results.pdf",
      "mimeType": "application/pdf",
      "storageUrl": "s3://clinic-files/prescriptions/665f1c2a/throat-culture-results.pdf",
      "uploadedAt": { "$date": "2026-06-11T14:25:00Z" }
    }
  ],

  "tags": ["antibiotic", "respiratory-infection", "short-course"],

  "metadata": {
    "createdBy": "doctor-portal",
    "clientVersion": "2.4.1",
    "sentToPharmacySystem": true,
    "lastModifiedAt": { "$date": "2026-06-18T09:13:02Z" }
  }
}
```

> Note: the capstone's `Prescription` Java model is a simplified, flat version of this design (`patientName`, `appointmentId`, `medication`, `dosage`, `doctorNotes`, plus a flat `refillCount` and `pharmacyName`). The richer design above models refills as a history array (`refills`) and the pharmacy as a nested object (`pharmacy`), whereas the Java model keeps them as single scalar fields. MongoDB's flexible schema means both shapes can coexist in the same collection during the course — `schemaVersion` distinguishes them, and documents missing `refillCount`/`pharmacyName` simply read back as `0`/`null`.

For reference, a document produced by the simplified Java `Prescription` model looks like this:

```json
{
  "_id": { "$oid": "665fa07712e4b7a8f0b99001" },
  "patientName": "Maria Petrova",
  "appointmentId": 51,
  "medication": "Amoxicillin",
  "dosage": "500mg",
  "doctorNotes": "Take with food. Complete the full 7-day course.",
  "refillCount": 1,
  "pharmacyName": "City Pharmacy #12"
}
```

### Thinking deeper: what would a chat message document look like?

If the system later adds patient–doctor messaging, MongoDB handles it far better than MySQL: messages are high-volume, append-only, and carry varied payloads (text, images, read receipts). A `messages` document would again reference MySQL IDs rather than embed users:

```json
{
  "_id": { "$oid": "665f9d11c2e4b7a8f0b67890" },
  "schemaVersion": 1,
  "conversationId": "patient17-doctor4",
  "senderId": 17,
  "senderRole": "PATIENT",
  "sentAt": { "$date": "2026-06-12T08:45:00Z" },
  "body": "Doctor, is it normal to feel drowsy after the second dose?",
  "attachments": [],
  "readBy": [
    { "userId": 4, "readAt": { "$date": "2026-06-12T09:02:00Z" } }
  ],
  "tags": ["medication-question"],
  "metadata": { "clientDevice": "ios", "edited": false }
}
```

### Will this design support schema evolution?

Yes, by construction:

- **Additive changes are free.** New optional fields can appear in new documents without touching the millions of existing ones.
- **`schemaVersion` makes change explicit.** The service layer knows how to read each version and can migrate documents lazily (upgrade-on-write) instead of in one big batch.
- **References keep MongoDB and MySQL loosely coupled.** Since documents store only IDs plus small display snapshots, changes to MySQL tables (new patient columns, renamed fields) never invalidate existing documents.
- **Arrays absorb growth.** "A prescription now supports multiple pharmacies" or "refills now need a rejection reason" become new array elements or new keys in embedded documents — no migration required.
