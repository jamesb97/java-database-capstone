package com.project.back_end.services;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import com.project.back_end.models.Appointment;
import com.project.back_end.models.Doctor;
import com.project.back_end.repo.AppointmentRepository;
import com.project.back_end.repo.DoctorRepository;

@org.springframework.stereotype.Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final TokenService tokenService;
    private final Service service;

    public AppointmentService(AppointmentRepository appointmentRepository, DoctorRepository doctorRepository,
            TokenService tokenService, Service service) {
        this.appointmentRepository = appointmentRepository;
        this.doctorRepository = doctorRepository;
        this.tokenService = tokenService;
        this.service = service;
    }

    /**
     * Persists a new appointment.
     *
     * @return 1 if the appointment was saved successfully, 0 otherwise.
     */
    @Transactional
    public int bookAppointment(Appointment appointment) {
        try {
            appointmentRepository.save(appointment);
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Updates an existing appointment after validating ownership, existence, and
     * doctor availability for the new time slot.
     */
    @Transactional
    public ResponseStatus updateAppointment(Appointment appointment) {
        Map<String, String> body = new HashMap<>();

        if (appointment.getId() == null) {
            body.put("message", "Appointment id is required for an update.");
            return new ResponseStatus(false, body);
        }

        Optional<Appointment> existingOpt = appointmentRepository.findById(appointment.getId());
        if (existingOpt.isEmpty()) {
            body.put("message", "Appointment not found.");
            return new ResponseStatus(false, body);
        }

        Appointment existing = existingOpt.get();

        // Ensure the appointment still belongs to the same patient.
        if (existing.getPatient() == null || appointment.getPatient() == null
                || existing.getPatient().getId() == null
                || !existing.getPatient().getId().equals(appointment.getPatient().getId())) {
            body.put("message", "This appointment does not belong to the requesting patient.");
            return new ResponseStatus(false, body);
        }

        // Ensure the requested slot is valid for the doctor.
        int slotValid = service.validateAppointment(appointment);
        if (slotValid == -1) {
            body.put("message", "The selected doctor does not exist.");
            return new ResponseStatus(false, body);
        }
        if (slotValid == 0) {
            body.put("message", "The selected time slot is not available.");
            return new ResponseStatus(false, body);
        }

        existing.setAppointmentTime(appointment.getAppointmentTime());
        existing.setDoctor(appointment.getDoctor());
        existing.setStatus(appointment.getStatus());
        appointmentRepository.save(existing);

        body.put("message", "Appointment updated successfully.");
        return new ResponseStatus(true, body);
    }

    /**
     * Cancels (deletes) an appointment after confirming the requesting patient owns it.
     */
    @Transactional
    public ResponseStatus cancelAppointment(long id, String token) {
        Map<String, String> body = new HashMap<>();

        Optional<Appointment> existingOpt = appointmentRepository.findById(id);
        if (existingOpt.isEmpty()) {
            body.put("message", "Appointment not found.");
            return new ResponseStatus(false, body);
        }

        Appointment existing = existingOpt.get();
        String email = tokenService.extractEmail(token);
        if (existing.getPatient() == null || existing.getPatient().getEmail() == null
                || !existing.getPatient().getEmail().equalsIgnoreCase(email)) {
            body.put("message", "You are not authorized to cancel this appointment.");
            return new ResponseStatus(false, body);
        }

        appointmentRepository.delete(existing);
        body.put("message", "Appointment cancelled successfully.");
        return new ResponseStatus(true, body);
    }

    /**
     * Retrieves a doctor's appointments for a given day, optionally filtered by patient name.
     * The doctor is resolved from the email embedded in the token.
     */
    @Transactional
    public Map<String, Object> getAppointments(String patientName, LocalDate date, String token) {
        Map<String, Object> result = new HashMap<>();

        String doctorEmail = tokenService.extractEmail(token);
        Doctor doctor = doctorRepository.findByEmail(doctorEmail);
        if (doctor == null) {
            result.put("message", "Doctor not found for the provided token.");
            result.put("appointments", List.of());
            return result;
        }

        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(java.time.LocalTime.MAX);

        List<Appointment> appointments;
        if (patientName == null || patientName.isBlank() || "null".equalsIgnoreCase(patientName)) {
            appointments = appointmentRepository
                    .findByDoctorIdAndAppointmentTimeBetween(doctor.getId(), start, end);
        } else {
            appointments = appointmentRepository
                    .findByDoctorIdAndPatient_NameContainingIgnoreCaseAndAppointmentTimeBetween(
                            doctor.getId(), patientName, start, end);
        }

        result.put("appointments", appointments);
        return result;
    }

    /**
     * Updates the status of an appointment (0 = scheduled, 1 = completed).
     */
    @Transactional
    public void changeStatus(int status, long id) {
        appointmentRepository.updateStatus(status, id);
    }

    /**
     * Small immutable result wrapper for service operations that need both a
     * success flag and a response body.
     */
    public static class ResponseStatus {
        private final boolean success;
        private final Map<String, String> body;

        public ResponseStatus(boolean success, Map<String, String> body) {
            this.success = success;
            this.body = body;
        }

        public boolean isSuccess() {
            return success;
        }

        public Map<String, String> getBody() {
            return body;
        }
    }
}
