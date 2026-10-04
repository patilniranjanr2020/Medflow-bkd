package com.medflow.modules.appointments.api.request;

import com.medflow.modules.appointments.api.AppointmentMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record BookAppointmentRequest(
    @NotNull Long patientId,
    @NotNull Long doctorId,
    @NotNull @Future Instant scheduledAt,
    AppointmentMode appointmentMode,
    @Min(5) @Max(240) Integer durationMinutes,
    @Size(max = 255) String reason,
    @Size(max = 1000) String notes) {
}
