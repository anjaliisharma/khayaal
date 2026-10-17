package com.khayaal.forecast;

import java.time.LocalDate;

public record RunOutForecast(
        LocalDate runOutDate,
        long daysLeft,
        LocalDate lastCoveredDate,
        LocalDate expectedRunOutDate,
        LocalDate reorderBy
) { }

