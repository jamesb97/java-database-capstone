package com.project.back_end.services;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.project.back_end.models.Appointment;
import com.project.back_end.models.Doctor;
import com.project.back_end.repo.DoctorRepository;

/**
 * Shared service for cross-cutting concerns such as token validation and
 * appointment-slot validation. Annotated with the fully-qualified Spring
 * annotation because this class is itself named {@code Service}.
 */
@org.springframework.stereotype.Service
public class Service {

    private final TokenService tokenService;
    private final DoctorRepository doctorRepository;

    public Service(TokenService tokenService, DoctorRepository doctorRepository) {
        this.tokenService = tokenService;
        this.doctorRepository = doctorRepository;
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
     * Validates whether the requested appointment time matches one of the doctor's
     * available slots.
     *
     * @return 1 if the time is available, 0 if it is not, -1 if the doctor does not exist.
     */
    public int validateAppointment(Appointment appointment) {
        if (appointment.getDoctor() == null || appointment.getDoctor().getId() == null) {
            return -1;
        }
        Optional<Doctor> doctorOpt = doctorRepository.findById(appointment.getDoctor().getId());
        if (doctorOpt.isEmpty()) {
            return -1;
        }
        Doctor doctor = doctorOpt.get();
        List<String> availableTimes = doctor.getAvailableTimes();
        if (availableTimes == null || availableTimes.isEmpty()) {
            return 0;
        }

        LocalDateTime appointmentTime = appointment.getAppointmentTime();
        if (appointmentTime == null) {
            return 0;
        }
        LocalTime requestedStart = appointmentTime.toLocalTime();

        for (String slot : availableTimes) {
            LocalTime slotStart = parseSlotStart(slot);
            if (slotStart != null && slotStart.equals(requestedStart)) {
                return 1;
            }
        }
        return 0;
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

    /**
     * Helper used by the appointment service to compute the [start, end) window for a day.
     */
    public LocalDateTime startOfDay(LocalDate date) {
        return date.atStartOfDay();
    }

    public LocalDateTime endOfDay(LocalDate date) {
        return date.atTime(LocalTime.MAX);
    }
}
