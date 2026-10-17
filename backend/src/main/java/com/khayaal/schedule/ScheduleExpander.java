package com.khayaal.schedule;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class ScheduleExpander {
    public List<ScheduledOccurrence> expand(
            ScheduleRule rule,
            ZoneId recipientZone,
            Instant rangeStartInclusive,
            Instant rangeEndExclusive
    ) {
        Objects.requireNonNull(rule, "rule is required");
        Objects.requireNonNull(recipientZone, "recipientZone is required");
        Objects.requireNonNull(rangeStartInclusive, "rangeStartInclusive is required");
        Objects.requireNonNull(rangeEndExclusive, "rangeEndExclusive is required");
        if (!rangeStartInclusive.isBefore(rangeEndExclusive)) {
            throw new IllegalArgumentException("range start must be before range end");
        }

        LocalDate firstDate = max(rule.startDate(), rangeStartInclusive.atZone(recipientZone).toLocalDate().minusDays(1));
        LocalDate lastDate = rangeEndExclusive.atZone(recipientZone).toLocalDate().plusDays(1);
        if (rule.endDate() != null) {
            lastDate = min(lastDate, rule.endDate());
        }

        List<ScheduledOccurrence> occurrences = new ArrayList<>();
        for (LocalDate date = firstDate; !date.isAfter(lastDate); date = date.plusDays(1)) {
            if (!matches(rule, date)) {
                continue;
            }
            for (var time : rule.localTimes()) {
                LocalDateTime requestedLocal = date.atTime(time);
                Instant scheduledAt = resolveLocalTime(requestedLocal, recipientZone);
                if (!scheduledAt.isBefore(rangeStartInclusive) && scheduledAt.isBefore(rangeEndExclusive)) {
                    LocalDateTime resolvedLocal = scheduledAt.atZone(recipientZone).toLocalDateTime();
                    occurrences.add(new ScheduledOccurrence(resolvedLocal, scheduledAt));
                }
            }
        }
        return occurrences.stream()
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private boolean matches(ScheduleRule rule, LocalDate date) {
        if (date.isBefore(rule.startDate()) || rule.endDate() != null && date.isAfter(rule.endDate())) {
            return false;
        }
        return switch (rule.type()) {
            case DAILY -> true;
            case WEEKDAYS -> rule.daysOfWeek().contains(date.getDayOfWeek());
            case EVERY_N_DAYS -> Math.floorMod(ChronoUnit.DAYS.between(rule.anchorDate(), date), rule.intervalDays()) == 0;
        };
    }

    /* DST policy: gaps shift forward by the transition duration; overlaps use the earlier offset. */
    private Instant resolveLocalTime(LocalDateTime requestedLocal, ZoneId zone) {
        ZoneRules rules = zone.getRules();
        List<ZoneOffset> offsets = rules.getValidOffsets(requestedLocal);
        if (!offsets.isEmpty()) {
            return requestedLocal.toInstant(offsets.getFirst());
        }
        ZoneOffsetTransition transition = rules.getTransition(requestedLocal);
        if (transition == null) {
            throw new DateTimeException("Cannot resolve local date-time " + requestedLocal + " in " + zone);
        }
        return transition.getDateTimeAfter().toInstant(transition.getOffsetAfter());
    }

    private LocalDate max(LocalDate left, LocalDate right) { return left.isAfter(right) ? left : right; }
    private LocalDate min(LocalDate left, LocalDate right) { return left.isBefore(right) ? left : right; }
}
