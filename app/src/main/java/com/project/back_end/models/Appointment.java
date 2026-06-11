package com.project.back_end.models;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

@Entity
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @NotNull
    private Doctor doctor;

    @ManyToOne
    @NotNull
    private Patient patient;

    @NotNull
    @Future
    private LocalDateTime appointmentTime;

    /**
     * Appointment status: 0 = scheduled, 1 = completed.
     */
    @NotNull
    private int status;

    public Appointment() {
    }

    public Appointment(Doctor doctor, Patient patient, LocalDateTime appointmentTime, int status) {
        this.doctor = doctor;
        this.patient = patient;
        this.appointmentTime = appointmentTime;
        this.status = status;
    }

    /**
     * Estimated end time of the appointment (one hour after the start time).
     * Not persisted in the database.
     */
    @Transient
    public LocalDateTime getEndTime() {
        return appointmentTime == null ? null : appointmentTime.plusHours(1);
    }

    /**
     * The date portion of the appointment time. Not persisted in the database.
     */
    @Transient
    public LocalDate getAppointmentDate() {
        return appointmentTime == null ? null : appointmentTime.toLocalDate();
    }

    /**
     * The time portion of the appointment time. Not persisted in the database.
     */
    @Transient
    public LocalTime getAppointmentTimeOnly() {
        return appointmentTime == null ? null : appointmentTime.toLocalTime();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Doctor getDoctor() {
        return doctor;
    }

    public void setDoctor(Doctor doctor) {
        this.doctor = doctor;
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public LocalDateTime getAppointmentTime() {
        return appointmentTime;
    }

    public void setAppointmentTime(LocalDateTime appointmentTime) {
        this.appointmentTime = appointmentTime;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }
}
