package com.khayaal.schedule;

import java.time.Instant;
import java.time.LocalDateTime;

public record ScheduledOccurrence(LocalDateTime scheduledLocal, Instant scheduledAt) implements Comparable<ScheduledOccurrence> {
    @Override
    public int compareTo(ScheduledOccurrence other) {
        return scheduledAt.compareTo(other.scheduledAt);
    }
}

