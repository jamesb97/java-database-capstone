package com.project.back_end.services;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.project.back_end.models.Admin;
import com.project.back_end.models.Appointment;
import com.project.back_end.models.Doctor;
import com.project.back_end.models.Patient;
import com.project.back_end.repo.AdminRepository;
import com.project.back_end.repo.DoctorRepository;
import com.project.back_end.repo.PatientRepository;

/**
 * Shared service for cross-cutting concerns such as token validation,
 * login, filtering, and appointment-slot validation. Annotated with the
 * fully-qualified Spring annotation because this class is itself named
 * {@code Service}.
 */
@org.springframework.stereotype.Service
public class Service {

    private final TokenService tokenService;
    private final AdminRepository adminRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final DoctorService doctorService;
    private final PatientService patientService;

    public Service(TokenService tokenService, AdminRepository adminRepository, DoctorRepository doctorRepository,
            PatientRepository patientRepository, DoctorService doctorService, PatientService patientService) {
        this.tokenService = tokenService;
        this.adminRepository = adminRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.doctorService = doctorService;
        this.patientService = patientService;
    }

    /**
     * Validates a JWT token for the given role. Returns an empty body with 200 OK
     * when valid, or a 401 Unauthorized with a message when invalid/expired.
     */
    public ResponseEntity<Map<String, String>> validateToken(String token, String role) {
        Map<String, String> response = new HashMap<>();
        if (!tokenService.validateToken(token, role)) {
            response.put("message", "Invalid or expired token. Please log in again.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Validates admin credentials and returns a JWT token on success.
     */
    public ResponseEntity<Map<String, String>> validateAdmin(Admin admin) {
        Map<String, String> response = new HashMap<>();
        try {
            if (admin == null || admin.getUsername() == null || admin.getPassword() == null) {
                response.put("message", "Username and password are required.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            Admin existing = adminRepository.findByUsername(admin.getUsername());
            if (existing == null || existing.getPassword() == null
                    || !existing.getPassword().equals(admin.getPassword())) {
                response.put("message", "Invalid username or password.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            response.put("token", tokenService.generateToken(existing.getUsername()));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("Error validating admin: " + e.getMessage());
            response.put("message", "Login failed due to an internal error.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Filters doctors by optional name, specialty, and AM/PM time period.
     * Missing or placeholder values (null / blank / "null") are ignored.
     */
    public Map<String, Object> filterDoctor(String name, String specialty, String time) {
        Map<String, Object> response = new HashMap<>();
        boolean hasName = isPresent(name);
        boolean hasSpecialty = isPresent(specialty);
        boolean hasTime = isPresent(time);

        List<Doctor> doctors;
        if (hasName && hasSpecialty && hasTime) {
            doctors = doctorService.filterDoctorsByNameSpecilityandTime(name, specialty, time);
        } else if (hasName && hasSpecialty) {
            doctors = doctorService.filterDoctorByNameAndSpecility(name, specialty);
        } else if (hasName && hasTime) {
            doctors = doctorService.filterDoctorByNameAndTime(name, time);
        } else if (hasSpecialty && hasTime) {
            doctors = doctorService.filterDoctorByTimeAndSpecility(specialty, time);
        } else if (hasName) {
            doctors = doctorService.findDoctorByName(name);
        } else if (hasSpecialty) {
            doctors = doctorService.filterDoctorBySpecility(specialty);
        } else if (hasTime) {
            doctors = doctorService.filterDoctorsByTime(time);
        } else {
            doctors = doctorService.getDoctors();
        }

        response.put("doctors", doctors);
        return response;
    }

    /**
     * Validates whether the requested appointment time is still available for the doctor.
     *
     * @return 1 if the time is available, 0 if it is not, -1 if the doctor does not exist.
     */
    public int validateAppointment(Appointment appointment) {
        if (appointment.getDoctor() == null || appointment.getDoctor().getId() == null) {
            return -1;
        }
        Long doctorId = appointment.getDoctor().getId();
        if (!doctorRepository.existsById(doctorId)) {
            return -1;
        }

        LocalDateTime appointmentTime = appointment.getAppointmentTime();
        if (appointmentTime == null) {
            return 0;
        }

        List<String> availableSlots = doctorService.getDoctorAvailability(doctorId, appointmentTime.toLocalDate());
        LocalTime requestedStart = appointmentTime.toLocalTime();
        for (String slot : availableSlots) {
            LocalTime slotStart = parseSlotStart(slot);
            if (slotStart != null && slotStart.equals(requestedStart)) {
                return 1;
            }
        }
        return 0;
    }

    /**
     * Returns true when no patient already exists with the same email or phone.
     */
    public boolean validatePatient(Patient patient) {
        if (patient == null) {
            return false;
        }
        return patientRepository.findByEmailOrPhone(patient.getEmail(), patient.getPhone()) == null;
    }

    /**
     * Validates patient credentials and returns a JWT token on success.
     */
    public ResponseEntity<Map<String, String>> validatePatientLogin(String email, String password) {
        Map<String, String> response = new HashMap<>();
        try {
            Patient patient = patientRepository.findByEmail(email);
            if (patient == null || patient.getPassword() == null || !patient.getPassword().equals(password)) {
                response.put("message", "Invalid email or password.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
            response.put("token", tokenService.generateToken(patient.getEmail()));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("Error validating patient login: " + e.getMessage());
            response.put("message", "Login failed due to an internal error.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Filters a patient's appointments using the email embedded in the token,
     * optionally by condition (past/future) and/or doctor name.
     */
    public ResponseEntity<Map<String, Object>> filterPatient(String condition, String name, String token) {
        try {
            String email = tokenService.extractEmail(token);
            Patient patient = patientRepository.findByEmail(email);
            if (patient == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("message", "Patient not found.");
                response.put("appointments", List.of());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            Long patientId = patient.getId();
            boolean hasCondition = isPresent(condition);
            boolean hasName = isPresent(name);

            if (hasCondition && hasName) {
                return patientService.filterByDoctorAndCondition(condition, name, patientId);
            }
            if (hasName) {
                return patientService.filterByDoctor(name, patientId);
            }
            if (hasCondition) {
                return patientService.filterByCondition(condition, patientId);
            }
            return patientService.getPatientAppointment(patientId);
        } catch (Exception e) {
            System.err.println("Error filtering patient appointments: " + e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Failed to filter appointments.");
            response.put("appointments", List.of());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Parses the start time from a slot string such as "09:00-10:00".
     */
    private LocalTime parseSlotStart(String slot) {
        if (slot == null || slot.isBlank()) {
            return null;
        }
        try {
            String start = slot.contains("-") ? slot.split("-")[0].trim() : slot.trim();
            return LocalTime.parse(start);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank() && !"null".equalsIgnoreCase(value);
    }

    /**
     * Helper used by the appointment service to compute the start of a day.
     */
    public LocalDateTime startOfDay(LocalDate date) {
        return date.atStartOfDay();
    }

    public LocalDateTime endOfDay(LocalDate date) {
        return date.atTime(LocalTime.MAX);
    }
}
