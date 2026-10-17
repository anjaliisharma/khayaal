package com.khayaal.adherence;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdherenceCalculatorTest {
    private final AdherenceCalculator calculator = new AdherenceCalculator();

    @Test
    void calculatesPercentagesWithSkippedExcludedFromDenominator() {
        var summary = calculator.calculate(List.of(
                record("2026-01-01", "08:00", DoseOutcome.TAKEN_ON_TIME),
                record("2026-01-01", "20:00", DoseOutcome.TAKEN_LATE),
                record("2026-01-02", "08:00", DoseOutcome.MISSED),
                record("2026-01-02", "20:00", DoseOutcome.SKIPPED)),
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-02"));
        assertThat(summary.takenOnTimePercentage()).isEqualByComparingTo("33.33");
        assertThat(summary.takenLatePercentage()).isEqualByComparingTo("33.33");
        assertThat(summary.missedPercentage()).isEqualByComparingTo("33.33");
        assertThat(summary.skipped()).isEqualTo(1);
    }

    @Test
    void buildsHeatmapStreakAndMostMissedTimes() {
        var summary = calculator.calculate(List.of(
                record("2026-01-01", "08:00", DoseOutcome.MISSED),
                record("2026-01-02", "08:00", DoseOutcome.TAKEN_ON_TIME),
                record("2026-01-02", "20:00", DoseOutcome.MISSED),
                record("2026-01-03", "08:00", DoseOutcome.TAKEN_ON_TIME),
                record("2026-01-04", "08:00", DoseOutcome.TAKEN_LATE)),
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-04"));
        assertThat(summary.dailyCompletion()).containsEntry(LocalDate.parse("2026-01-01"), DailyCompletion.MISSED)
                .containsEntry(LocalDate.parse("2026-01-02"), DailyCompletion.PARTIALLY_COMPLETED)
                .containsEntry(LocalDate.parse("2026-01-04"), DailyCompletion.FULLY_COMPLETED);
        assertThat(summary.currentFullyCompletedDayStreak()).isEqualTo(2);
        assertThat(summary.mostMissedTimes()).containsExactly(
                new MissedTime(LocalTime.of(8, 0), 1), new MissedTime(LocalTime.of(20, 0), 1));
    }

    @Test
    void ignoresOutOfRangeRecordsAndHandlesOnlySkippedRecords() {
        var summary = calculator.calculate(List.of(
                record("2025-12-31", "08:00", DoseOutcome.MISSED),
                record("2026-01-01", "08:00", DoseOutcome.SKIPPED)),
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-01"));
        assertThat(summary.missed()).isZero();
        assertThat(summary.missedPercentage()).isEqualByComparingTo("0.00");
        assertThat(summary.dailyCompletion()).isEmpty();
    }

    @Test
    void rejectsInvertedRange() {
        assertThatThrownBy(() -> calculator.calculate(List.of(), LocalDate.parse("2026-01-02"), LocalDate.parse("2026-01-01")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private DoseRecord record(String date, String time, DoseOutcome outcome) {
        return new DoseRecord(LocalDate.parse(date), LocalTime.parse(time), outcome);
    }
}
