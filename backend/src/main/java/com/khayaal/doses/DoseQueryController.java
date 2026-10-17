package com.khayaal.doses;

import com.khayaal.adherence.*;
import com.khayaal.identity.CurrentUser;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class DoseQueryController {
    private static final Duration ON_TIME_WINDOW = Duration.ofMinutes(15);
    private final JdbcClient jdbc;
    private final Clock clock;
    private final AdherenceCalculator adherence = new AdherenceCalculator();

    public DoseQueryController(JdbcClient jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }

    @GetMapping("/doses/today")
    List<TodayDose> today(@RequestParam(required=false) UUID person, Authentication authentication) {
        UUID user = CurrentUser.require(authentication).userId();
        String personPredicate = person == null ? "" : "and r.id=:person ";
        var query = jdbc.sql("select d.id,d.scheduled_at,d.scheduled_local,d.status,d.acted_by,actor.display_name,m.id,m.name,m.strength,r.id,r.name from dose_events d " +
                        "join medicines m on m.id=d.medicine_id join care_recipients r on r.id=m.recipient_id join household_memberships hm on hm.household_id=r.household_id " +
                        "left join user_accounts actor on actor.id=d.acted_by " +
                        "where hm.user_id=:user " + personPredicate +
                        "and d.scheduled_local::date=(current_timestamp at time zone r.timezone)::date order by d.scheduled_at,m.name")
                .param("user", user);
        if (person != null) query = query.param("person", person);
        return query
                .query((rs, row) -> new TodayDose(rs.getObject(1, UUID.class), rs.getObject(2, OffsetDateTime.class).toInstant(),
                        rs.getObject(3, LocalDateTime.class), rs.getString(4), rs.getObject(5, UUID.class), rs.getString(6), rs.getObject(7, UUID.class),
                        rs.getString(8), rs.getString(9), rs.getObject(10, UUID.class), rs.getString(11))).list();
    }

    @GetMapping("/adherence")
    AdherenceSummary adherence(@RequestParam UUID person, @RequestParam(defaultValue="30") int range, Authentication authentication) {
        if (range != 7 && range != 30 && range != 90) throw new IllegalArgumentException("range must be 7, 30, or 90 days");
        UUID user = CurrentUser.require(authentication).userId();
        LocalDate end = LocalDate.now(clock); LocalDate start = end.minusDays(range - 1L);
        List<DoseRecord> records = jdbc.sql("select d.scheduled_local,d.scheduled_at,d.acted_at,d.status from dose_events d join medicines m on m.id=d.medicine_id " +
                        "join care_recipients r on r.id=m.recipient_id join household_memberships hm on hm.household_id=r.household_id " +
                        "where r.id=:person and hm.user_id=:user and d.scheduled_local::date between :start and :end and d.status<>'PENDING'")
                .param("person", person).param("user", user).param("start", start).param("end", end)
                .query((rs, row) -> {
                    LocalDateTime local = rs.getObject(1, LocalDateTime.class);
                    Instant scheduled = rs.getObject(2, OffsetDateTime.class).toInstant();
                    OffsetDateTime actedValue = rs.getObject(3, OffsetDateTime.class);
                    String status = rs.getString(4);
                    DoseOutcome outcome = switch (status) {
                        case "SKIPPED" -> DoseOutcome.SKIPPED;
                        case "MISSED" -> DoseOutcome.MISSED;
                        case "TAKEN" -> actedValue != null && actedValue.toInstant().isAfter(scheduled.plus(ON_TIME_WINDOW))
                                ? DoseOutcome.TAKEN_LATE : DoseOutcome.TAKEN_ON_TIME;
                        default -> throw new IllegalStateException("Unexpected persisted dose status " + status);
                    };
                    return new DoseRecord(local.toLocalDate(), local.toLocalTime(), outcome);
                }).list();
        return adherence.calculate(records, start, end);
    }

    record TodayDose(UUID id,Instant scheduledAt,LocalDateTime scheduledLocal,String status,UUID actedBy,String actedByName,
                     UUID medicineId,String medicineName,String strength,UUID recipientId,String recipientName) { }
}
