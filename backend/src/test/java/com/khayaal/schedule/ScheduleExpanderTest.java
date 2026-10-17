package com.khayaal.schedule;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduleExpanderTest {
    private final ScheduleExpander expander = new ScheduleExpander();

    @Test
    void expandsDailyTimesInChronologicalOrder() {
        var rule = ScheduleRule.daily(LocalDate.parse("2026-01-01"), null,
                List.of(LocalTime.of(20, 0), LocalTime.of(8, 0)));
        var result = expander.expand(rule, ZoneId.of("Asia/Kolkata"),
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-03T00:00:00Z"));
        assertThat(result).extracting(ScheduledOccurrence::scheduledLocal).containsExactly(
                LocalDate.parse("2026-01-01").atTime(8, 0), LocalDate.parse("2026-01-01").atTime(20, 0),
                LocalDate.parse("2026-01-02").atTime(8, 0), LocalDate.parse("2026-01-02").atTime(20, 0));
    }

    @Test
    void expandsSelectedWeekdaysOnly() {
        var rule = ScheduleRule.weekdays(LocalDate.parse("2026-01-01"), null,
                Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), List.of(LocalTime.NOON));
        var result = expander.expand(rule, ZoneId.of("UTC"), Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-09T00:00:00Z"));
        assertThat(result).extracting(value -> value.scheduledLocal().toLocalDate())
                .containsExactly(LocalDate.parse("2026-01-05"), LocalDate.parse("2026-01-07"));
    }

    @Test
    void expandsEveryNDaysRelativeToAnchor() {
        var rule = ScheduleRule.everyNDays(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-08"), 3,
                LocalDate.parse("2026-01-01"), List.of(LocalTime.of(9, 0)));
        var result = expander.expand(rule, ZoneId.of("UTC"), Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-10T00:00:00Z"));
        assertThat(result).extracting(value -> value.scheduledLocal().toLocalDate()).containsExactly(
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-04"), LocalDate.parse("2026-01-07"));
    }

    @Test
    void shiftsDstGapForwardAndUsesEarlierOffsetForOverlap() {
        ZoneId zone = ZoneId.of("America/New_York");
        var spring = ScheduleRule.daily(LocalDate.parse("2026-03-08"), LocalDate.parse("2026-03-08"), List.of(LocalTime.of(2, 30)));
        var autumn = ScheduleRule.daily(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-01"), List.of(LocalTime.of(1, 30)));
        var shifted = expander.expand(spring, zone, Instant.parse("2026-03-08T00:00:00Z"), Instant.parse("2026-03-09T00:00:00Z")).getFirst();
        assertThat(shifted.scheduledAt())
                .isEqualTo(Instant.parse("2026-03-08T07:00:00Z"));
        assertThat(shifted.scheduledLocal()).isEqualTo(LocalDate.parse("2026-03-08").atTime(3, 0));
        assertThat(expander.expand(autumn, zone, Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-11-02T00:00:00Z")).getFirst().scheduledAt())
                .isEqualTo(Instant.parse("2026-11-01T05:30:00Z"));
    }

    @Test
    void validatesRulesAndRange() {
        assertThatThrownBy(() -> ScheduleRule.weekdays(LocalDate.now(), null, Set.of(), List.of(LocalTime.NOON)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScheduleRule.everyNDays(LocalDate.now(), null, 0, LocalDate.now(), List.of(LocalTime.NOON)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScheduleRule.daily(LocalDate.now(), null, List.of(
                LocalTime.of(1, 0), LocalTime.of(2, 0), LocalTime.of(3, 0), LocalTime.of(4, 0),
                LocalTime.of(5, 0), LocalTime.of(6, 0), LocalTime.of(7, 0))))
                .isInstanceOf(IllegalArgumentException.class);
        var rule = ScheduleRule.daily(LocalDate.now(), null, List.of(LocalTime.NOON));
        assertThatThrownBy(() -> expander.expand(rule, ZoneId.of("UTC"), Instant.EPOCH, Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

