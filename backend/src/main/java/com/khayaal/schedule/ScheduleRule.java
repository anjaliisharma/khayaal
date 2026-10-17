package com.khayaal.schedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ScheduleRule(
        ScheduleType type,
        LocalDate startDate,
        LocalDate endDate,
        Set<DayOfWeek> daysOfWeek,
        int intervalDays,
        LocalDate anchorDate,
        List<LocalTime> localTimes
) {
    public ScheduleRule {
        Objects.requireNonNull(type, "type is required");
        Objects.requireNonNull(startDate, "startDate is required");
        daysOfWeek = Set.copyOf(daysOfWeek == null ? Set.of() : daysOfWeek);
        localTimes = localTimes == null ? List.of() : localTimes.stream().distinct().sorted().toList();
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate must not be before startDate");
        }
        if (localTimes.isEmpty()) {
            throw new IllegalArgumentException("at least one local time is required");
        }
        if (localTimes.size() > 6) {
            throw new IllegalArgumentException("at most six local times are allowed per rule");
        }
        if (type == ScheduleType.WEEKDAYS && daysOfWeek.isEmpty()) {
            throw new IllegalArgumentException("WEEKDAYS requires at least one day");
        }
        if (type == ScheduleType.EVERY_N_DAYS && (intervalDays < 1 || anchorDate == null)) {
            throw new IllegalArgumentException("EVERY_N_DAYS requires a positive interval and anchorDate");
        }
    }

    public static ScheduleRule daily(LocalDate startDate, LocalDate endDate, List<LocalTime> localTimes) {
        return new ScheduleRule(ScheduleType.DAILY, startDate, endDate, Set.of(), 0, null, localTimes);
    }

    public static ScheduleRule weekdays(LocalDate startDate, LocalDate endDate, Set<DayOfWeek> days, List<LocalTime> localTimes) {
        return new ScheduleRule(ScheduleType.WEEKDAYS, startDate, endDate, days, 0, null, localTimes);
    }

    public static ScheduleRule everyNDays(LocalDate startDate, LocalDate endDate, int intervalDays, LocalDate anchorDate, List<LocalTime> localTimes) {
        return new ScheduleRule(ScheduleType.EVERY_N_DAYS, startDate, endDate, Set.of(), intervalDays, anchorDate, localTimes);
    }
}

