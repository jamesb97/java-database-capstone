-- Reporting stored procedures for the MySQL side of the clinic schema.
-- Requires MySQL 8.0+ (CTEs and window functions).
--
-- Load them:
--   docker compose exec -T mysql mysql -uroot -proot mydb < sql/stored_procedures.sql
--
-- Call them:
--   CALL GetTopDoctorPerMonth();
--   CALL GetDoctorWithMostPatientsByMonth(2026, 8);
--   CALL GetAppointmentCountsByMonth();
--   CALL GetDoctorWithMostPatientsByYear(2026);
--
-- Patient counts are DISTINCT patients, so a patient seen three times in one
-- month counts once. Ties are all returned rather than broken arbitrarily.

USE mydb;

DROP PROCEDURE IF EXISTS GetTopDoctorPerMonth;
DROP PROCEDURE IF EXISTS GetDoctorWithMostPatientsByMonth;
DROP PROCEDURE IF EXISTS GetAppointmentCountsByMonth;
DROP PROCEDURE IF EXISTS GetDoctorWithMostPatientsByYear;

DELIMITER //

-- Busiest doctor in every month that has at least one appointment.
CREATE PROCEDURE GetTopDoctorPerMonth()
BEGIN
    WITH monthly AS (
        SELECT
            DATE_FORMAT(a.appointment_time, '%Y-%m') AS ym,
            a.doctor_id,
            COUNT(DISTINCT a.patient_id) AS patient_count,
            COUNT(*) AS appointment_count
        FROM appointment a
        GROUP BY ym, a.doctor_id
    ),
    ranked AS (
        SELECT
            monthly.*,
            RANK() OVER (PARTITION BY ym ORDER BY patient_count DESC) AS rnk
        FROM monthly
    )
    SELECT
        r.ym AS month,
        d.id AS doctor_id,
        d.name AS doctor,
        d.specialty,
        r.patient_count,
        r.appointment_count
    FROM ranked r
    JOIN doctor d ON d.id = r.doctor_id
    WHERE r.rnk = 1
    ORDER BY r.ym, d.name;
END //

-- Busiest doctor in one specific month.
CREATE PROCEDURE GetDoctorWithMostPatientsByMonth(
    IN p_year INT,
    IN p_month INT
)
BEGIN
    DECLARE v_start DATETIME;
    DECLARE v_end DATETIME;

    IF p_year IS NULL OR p_month IS NULL OR p_month < 1 OR p_month > 12 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'p_year is required and p_month must be between 1 and 12';
    END IF;

    -- Half-open range instead of YEAR()/MONTH() so the appointment_time index stays usable.
    SET v_start = MAKEDATE(p_year, 1) + INTERVAL (p_month - 1) MONTH;
    SET v_end = v_start + INTERVAL 1 MONTH;

    WITH monthly AS (
        SELECT
            a.doctor_id,
            COUNT(DISTINCT a.patient_id) AS patient_count,
            COUNT(*) AS appointment_count
        FROM appointment a
        WHERE a.appointment_time >= v_start
          AND a.appointment_time < v_end
        GROUP BY a.doctor_id
    ),
    ranked AS (
        SELECT
            monthly.*,
            RANK() OVER (ORDER BY patient_count DESC) AS rnk
        FROM monthly
    )
    SELECT
        DATE_FORMAT(v_start, '%Y-%m') AS month,
        d.id AS doctor_id,
        d.name AS doctor,
        d.specialty,
        r.patient_count,
        r.appointment_count
    FROM ranked r
    JOIN doctor d ON d.id = r.doctor_id
    WHERE r.rnk = 1
    ORDER BY d.name;
END //

-- Admin usage statistics: appointment volume per month across all doctors.
CREATE PROCEDURE GetAppointmentCountsByMonth()
BEGIN
    SELECT
        DATE_FORMAT(a.appointment_time, '%Y-%m') AS month,
        COUNT(*) AS appointment_count,
        COUNT(DISTINCT a.patient_id) AS distinct_patients,
        COUNT(DISTINCT a.doctor_id) AS active_doctors
    FROM appointment a
    GROUP BY month
    ORDER BY month;
END //

DELIMITER ;

-- Get doctor with most patients by year.
CREATE PROCEDURE GetDoctorWithMostPatientsByYear(
    IN p_year INT
)
BEGIN
    DECLARE v_start DATETIME;
    DECLARE v_end DATETIME;

    IF p_year IS NULL OR p_year < 1900 OR p_year > 2100 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'p_year must be between 1900 and 2100';
    END IF;

    SET v_start = MAKEDATE(p_year, 1);
    SET v_end = v_start + INTERVAL 1 YEAR;

    WITH yearly AS (
        SELECT
            a.doctor_id,
            COUNT(DISTINCT a.patient_id) AS patient_count,
            COUNT(*) AS appointment_count
        FROM appointment a
        WHERE a.appointment_time >= v_start
          AND a.appointment_time < v_end
        GROUP BY a.doctor_id
    ),
    ranked AS (
        SELECT
            yearly.*,
            RANK() OVER (ORDER BY patient_count DESC) AS rnk
        FROM yearly
    )
    SELECT
        p_year AS year,
        d.id AS doctor_id,
        d.name AS doctor,
        d.specialty,
        r.patient_count,
        r.appointment_count
    FROM ranked r
    JOIN doctor d ON d.id = r.doctor_id
    WHERE r.rnk = 1
    ORDER BY d.name;
END //
