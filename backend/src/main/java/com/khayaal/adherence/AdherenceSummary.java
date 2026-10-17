package com.khayaal.adherence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record AdherenceSummary(
        long takenOnTime,
        long takenLate,
        long skipped,
        long missed,
        BigDecimal takenOnTimePercentage,
        BigDecimal takenLatePercentage,
        BigDecimal missedPercentage,
        Map<LocalDate, DailyCompletion> dailyCompletion,
        int currentFullyCompletedDayStreak,
        List<MissedTime> mostMissedTimes
) { }

