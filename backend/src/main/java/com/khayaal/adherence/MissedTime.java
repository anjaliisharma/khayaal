package com.khayaal.adherence;

import java.time.LocalTime;

public record MissedTime(LocalTime scheduledTime, long missedCount) { }

