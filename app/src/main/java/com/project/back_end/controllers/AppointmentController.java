package com.project.back_end.controllers;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.project.back_end.models.Appointment;
import com.project.back_end.services.AppointmentService;
import com.project.back_end.services.Service;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final Service service;

    public AppointmentController(AppointmentService appointmentService, Service service) {
        this.appointmentService = appointmentService;
        this.service = service;
    }

    @GetMapping("/{date}/{patientName}/{token}")
    public ResponseEntity<Map<String, Object>> getAppointments(
            @PathVariable LocalDate date,
            @PathVariable String patientName,
            @PathVariable String token) {

        ResponseEntity<Map<String, String>> tokenCheck = service.validateToken(token, "doctor");
        if (tokenCheck.getStatusCode() != HttpStatus.OK) {
            Map<String, Object> body = new HashMap<>(tokenCheck.getBody());
            return ResponseEntity.status(tokenCheck.getStatusCode()).body(body);
        }

        Map<String, Object> appointments = appointmentService.getAppointments(patientName, date, token);
        return ResponseEntity.ok(appointments);
    }

    /**
     * Books a new appointment. The {@code @Valid} annotation triggers the model
     * constraints — most importantly {@code @Future} on {@code appointmentTime},
     * which rejects any attempt to schedule in the past.
     */
    @PostMapping("/{token}")
    public ResponseEntity<Map<String, String>> bookAppointment(
            @Valid @RequestBody Appointment appointment,
            @PathVariable String token) {

        ResponseEntity<Map<String, String>> tokenCheck = service.validateToken(token, "patient");
        if (tokenCheck.getStatusCode() != HttpStatus.OK) {
            return tokenCheck;
        }

        Map<String, String> response = new HashMap<>();
        int slotValid = service.validateAppointment(appointment);
        if (slotValid == -1) {
            response.put("message", "The selected doctor does not exist.");
            return ResponseEntity.badRequest().body(response);
        }
        if (slotValid == 0) {
            response.put("message", "The selected time slot is not available.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        int booked = appointmentService.bookAppointment(appointment);
        if (booked == 1) {
            response.put("message", "Appointment booked successfully.");
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }

        response.put("message", "Failed to book the appointment. Please try again.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @PutMapping("/{token}")
    public ResponseEntity<Map<String, String>> updateAppointment(
            @Valid @RequestBody Appointment appointment,
            @PathVariable String token) {

        ResponseEntity<Map<String, String>> tokenCheck = service.validateToken(token, "patient");
        if (tokenCheck.getStatusCode() != HttpStatus.OK) {
            return tokenCheck;
        }

        AppointmentService.ResponseStatus result = appointmentService.updateAppointment(appointment);
        if (result.isSuccess()) {
            return ResponseEntity.ok(result.getBody());
        }
        return ResponseEntity.badRequest().body(result.getBody());
    }

    @DeleteMapping("/{id}/{token}")
    public ResponseEntity<Map<String, String>> cancelAppointment(
            @PathVariable long id,
            @PathVariable String token) {

        ResponseEntity<Map<String, String>> tokenCheck = service.validateToken(token, "patient");
        if (tokenCheck.getStatusCode() != HttpStatus.OK) {
            return tokenCheck;
        }

        AppointmentService.ResponseStatus result = appointmentService.cancelAppointment(id, token);
        if (result.isSuccess()) {
            return ResponseEntity.ok(result.getBody());
        }
        return ResponseEntity.badRequest().body(result.getBody());
    }
}
