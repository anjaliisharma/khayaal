package com.khayaal.schedule;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleExpanderPropertyTest {
    @Property
    void expansionIsDeterministic(@ForAll("validDays") int days) {
        var rule = ScheduleRule.daily(LocalDate.parse("2026-01-01"), null, List.of(LocalTime.of(8, 0), LocalTime.of(20, 0)));
        Instant end = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(days * 86_400L);
        var expander = new ScheduleExpander();
        assertThat(expander.expand(rule, ZoneId.of("UTC"), Instant.parse("2026-01-01T00:00:00Z"), end))
                .isEqualTo(expander.expand(rule, ZoneId.of("UTC"), Instant.parse("2026-01-01T00:00:00Z"), end));
    }

    @net.jqwik.api.Provide
    net.jqwik.api.Arbitrary<Integer> validDays() { return net.jqwik.api.Arbitraries.integers().between(1, 30); }
}
