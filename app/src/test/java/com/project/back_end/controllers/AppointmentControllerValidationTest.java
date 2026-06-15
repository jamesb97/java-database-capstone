package com.project.back_end.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.project.back_end.models.Appointment;
import com.project.back_end.services.AppointmentService;
import com.project.back_end.services.Service;

/**
 * Web-layer tests that lock in the "no past scheduling" rule. {@code @Valid} on
 * the controller runs during request binding, so an invalid (past) appointment
 * time is rejected with a 400 before any service code executes.
 *
 * Uses a standalone MockMvc setup (no Spring context / database) wired with the
 * {@link ValidationFailed} advice so bean-validation errors map to HTTP 400.
 */
class AppointmentControllerValidationTest {

    private static final String TOKEN = "fake-token";

    private MockMvc mockMvc;
    private AppointmentService appointmentService;
    private Service service;

    @BeforeEach
    void setUp() {
        appointmentService = mock(AppointmentService.class);
        service = mock(Service.class);
        AppointmentController controller = new AppointmentController(appointmentService, service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ValidationFailed())
                .build();
    }

    private String appointmentJson(String appointmentTime) {
        return "{"
                + "\"doctor\": {\"id\": 1},"
                + "\"patient\": {\"id\": 1},"
                + "\"appointmentTime\": \"" + appointmentTime + "\","
                + "\"status\": 0"
                + "}";
    }

    @Test
    void bookingPastAppointmentReturnsBadRequest() throws Exception {
        // Token would be valid, to prove the 400 comes from @Future and not auth.
        when(service.validateToken(anyString(), anyString()))
                .thenReturn(ResponseEntity.ok(new HashMap<>()));

        mockMvc.perform(post("/appointments/{token}", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentJson("2020-01-01T10:00:00")))
                .andExpect(status().isBadRequest());

        // The request never reached the booking logic.
        verify(appointmentService, never()).bookAppointment(any(Appointment.class));
    }

    @Test
    void bookingFutureAppointmentPassesValidation() throws Exception {
        // Make the token invalid so the call returns 401. Reaching the token check
        // at all proves @Valid accepted the future-dated body.
        Map<String, String> unauthorized = new HashMap<>();
        unauthorized.put("message", "Invalid or expired token. Please log in again.");
        when(service.validateToken(anyString(), anyString()))
                .thenReturn(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(unauthorized));

        mockMvc.perform(post("/appointments/{token}", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentJson("2999-01-01T10:00:00")))
                .andExpect(status().isUnauthorized());

        verify(appointmentService, never()).bookAppointment(any(Appointment.class));
    }
}
