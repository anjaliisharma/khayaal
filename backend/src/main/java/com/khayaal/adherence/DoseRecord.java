package com.khayaal.adherence;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

public record DoseRecord(LocalDate scheduledDate, LocalTime scheduledTime, DoseOutcome outcome) {
    public DoseRecord {
        Objects.requireNonNull(scheduledDate, "scheduledDate is required");
        Objects.requireNonNull(scheduledTime, "scheduledTime is required");
        Objects.requireNonNull(outcome, "outcome is required");
    }
}

