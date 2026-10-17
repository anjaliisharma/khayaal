package com.khayaal.doses;

import com.khayaal.stock.StockService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
@EnableConfigurationProperties(DoseActionProperties.class)
public class DoseActionService {
    private final JdbcClient jdbc;
    private final StockService stock;
    private final Clock clock;
    private final DoseActionProperties properties;

    public DoseActionService(JdbcClient jdbc, StockService stock, Clock clock, DoseActionProperties properties) {
        this.jdbc = jdbc;
        this.stock = stock;
        this.clock = clock;
        this.properties = properties;
    }

    @Transactional
    public DoseActionResult act(UUID doseEventId, UUID actorId, String action, String idempotencyKey,
                                Instant actualTime, String note, String clientIdentifier) {
        if (idempotencyKey == null || idempotencyKey.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key header is required");
        DoseRow dose = lockDose(doseEventId, actorId);
        PriorDoseAction prior = jdbc.sql("select dose_event_id,action,result_status,result_acted_at,result_acted_by,result_note from dose_action_requests where user_id=:user and idempotency_key=:key")
                .param("user", actorId).param("key", idempotencyKey)
                .query((rs, row) -> new PriorDoseAction(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getObject(4, java.time.OffsetDateTime.class) == null ? null : rs.getObject(4, java.time.OffsetDateTime.class).toInstant(),
                        rs.getObject(5, UUID.class), rs.getString(6))).optional().orElse(null);
        if (prior != null) {
            if (!prior.doseEventId().equals(doseEventId) || !prior.action().equals(action))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key was used for another action");
            return new DoseActionResult(prior.doseEventId(), prior.resultStatus(), prior.actedAt(), prior.actedBy(), prior.note());
        }

        Instant now = clock.instant();
        switch (action) {
            case "TAKEN" -> take(dose, actorId, actualTime == null ? now : actualTime, note, now);
            case "SKIPPED" -> skip(dose, actorId, note, now);
            case "UNDO" -> undo(dose, actorId, now);
            default -> throw new IllegalArgumentException("Unsupported dose action");
        }
        DoseActionResult result = result(doseEventId);
        jdbc.sql("insert into dose_action_requests(user_id,idempotency_key,dose_event_id,action,result_status,result_acted_at,result_acted_by,result_note,created_at) " +
                        "values (:user,:key,:dose,:action,:status,:actedAt,:actedBy,:note,:at)")
                .param("user", actorId).param("key", idempotencyKey).param("dose", doseEventId).param("action", action)
                .param("status", result.status()).param("actedAt", result.actedAt() == null ? null : java.time.OffsetDateTime.ofInstant(result.actedAt(), java.time.ZoneOffset.UTC), java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
                .param("actedBy", result.actedBy()).param("note", result.note())
                .param("at", Timestamp.from(now)).update();
        audit(dose.householdId(), actorId, doseEventId, action, clientIdentifier, now);
        return result;
    }

    @Transactional
    public DoseActionResult logPrn(UUID medicineId, UUID actorId, String idempotencyKey, Instant actualTime,
                                   String note, String clientIdentifier) {
        MedicineForPrn medicine = jdbc.sql("select m.units_per_dose,r.household_id,r.timezone,m.as_needed from medicines m join care_recipients r on r.id=m.recipient_id " +
                        "join household_memberships hm on hm.household_id=r.household_id where m.id=:medicine and hm.user_id=:user and hm.role<>'VIEWER'")
                .param("medicine", medicineId).param("user", actorId)
                .query((rs, row) -> new MedicineForPrn(rs.getBigDecimal(1), rs.getObject(2, UUID.class), rs.getString(3), rs.getBoolean(4)))
                .optional().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medicine not found"));
        if (!medicine.asNeeded()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Only as-needed medicines can be logged without a schedule");
        Instant occurredAt = actualTime == null ? clock.instant() : actualTime;
        UUID doseId = UUID.randomUUID();
        jdbc.sql("insert into dose_events(id,medicine_id,scheduled_at,scheduled_local,status) values (:id,:medicine,:at,:local,'PENDING')")
                .param("id", doseId).param("medicine", medicineId).param("at", Timestamp.from(occurredAt))
                .param("local", LocalDateTime.ofInstant(occurredAt, ZoneId.of(medicine.zone()))).update();
        return act(doseId, actorId, "TAKEN", idempotencyKey, occurredAt, note, clientIdentifier);
    }

    @Transactional
    public int markOverduePendingDosesMissed(Instant asOf) {
        Instant cutoff = asOf.minusSeconds(properties.missedGraceMinutes() * 60L);
        var missed = jdbc.sql("update dose_events d set status='MISSED' from medicines m,care_recipients r " +
                        "where d.medicine_id=m.id and m.recipient_id=r.id and d.status='PENDING' and d.scheduled_at<=:cutoff returning d.id,r.household_id")
                .param("cutoff", Timestamp.from(cutoff))
                .query((rs, row) -> new MissedDose(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class))).list();
        missed.forEach(dose -> audit(dose.householdId(), null, dose.id(), "MISSED", "scheduled-sweep", asOf));
        return missed.size();
    }

    private void take(DoseRow dose, UUID actor, Instant actedAt, String note, Instant now) {
        if (dose.status().equals("TAKEN")) return;
        if (!dose.status().equals("PENDING") && !dose.status().equals("MISSED"))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dose cannot transition from " + dose.status() + " to TAKEN");
        jdbc.sql("update dose_events set status='TAKEN',acted_at=:actedAt,acted_by=:actor,note=:note where id=:id")
                .param("actedAt", Timestamp.from(actedAt)).param("actor", actor).param("note", note).param("id", dose.id()).update();
        stock.consumeForDose(dose.medicineId(), dose.id(), dose.unitsPerDose(), dose.scheduledLocal().toLocalDate(), actor);
    }

    private void skip(DoseRow dose, UUID actor, String note, Instant now) {
        if (dose.status().equals("SKIPPED")) return;
        if (!dose.status().equals("PENDING"))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending doses can be skipped");
        jdbc.sql("update dose_events set status='SKIPPED',acted_at=:at,acted_by=:actor,note=:note where id=:id")
                .param("at", Timestamp.from(now)).param("actor", actor).param("note", note).param("id", dose.id()).update();
    }

    private void undo(DoseRow dose, UUID actor, Instant now) {
        if (!dose.status().equals("TAKEN")) throw new ResponseStatusException(HttpStatus.CONFLICT, "Only taken doses can be undone");
        if (dose.actedAt() == null || dose.actedAt().plusSeconds(properties.undoWindowMinutes() * 60L).isBefore(now))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dose undo window has elapsed");
        stock.undoDose(dose.medicineId(), dose.id(), actor);
        jdbc.sql("update dose_events set status='PENDING',acted_at=null,acted_by=null,note=null where id=:id")
                .param("id", dose.id()).update();
    }

    private DoseRow lockDose(UUID doseId, UUID actorId) {
        return jdbc.sql("select d.id,d.medicine_id,d.status,d.acted_at,d.scheduled_local,m.units_per_dose,r.household_id from dose_events d " +
                        "join medicines m on m.id=d.medicine_id join care_recipients r on r.id=m.recipient_id " +
                        "join household_memberships hm on hm.household_id=r.household_id where d.id=:dose and hm.user_id=:user and hm.role<>'VIEWER' for update of d")
                .param("dose", doseId).param("user", actorId).query((rs, row) -> new DoseRow(rs.getObject(1, UUID.class),
                        rs.getObject(2, UUID.class), rs.getString(3), rs.getObject(4, java.time.OffsetDateTime.class) == null ? null : rs.getObject(4, java.time.OffsetDateTime.class).toInstant(),
                        rs.getObject(5, LocalDateTime.class), rs.getBigDecimal(6), rs.getObject(7, UUID.class)))
                .optional().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dose event not found"));
    }

    private DoseActionResult result(UUID doseId) {
        return jdbc.sql("select id,status,acted_at,acted_by,note from dose_events where id=:id").param("id", doseId)
                .query((rs, row) -> new DoseActionResult(rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getObject(3, java.time.OffsetDateTime.class) == null ? null : rs.getObject(3, java.time.OffsetDateTime.class).toInstant(),
                        rs.getObject(4, UUID.class), rs.getString(5))).single();
    }

    private void audit(UUID household, UUID actor, UUID entity, String action, String client, Instant at) {
        jdbc.sql("insert into audit_events(id,household_id,actor_id,entity_type,entity_id,action,client_identifier,occurred_at) values (:id,:household,:actor,'DOSE_EVENT',:entity,:action,:client,:at)")
                .param("id", UUID.randomUUID()).param("household", household).param("actor", actor).param("entity", entity)
                .param("action", action).param("client", client).param("at", Timestamp.from(at)).update();
    }

    private record DoseRow(UUID id, UUID medicineId, String status, Instant actedAt, LocalDateTime scheduledLocal,
                           java.math.BigDecimal unitsPerDose, UUID householdId) { }
    private record MedicineForPrn(java.math.BigDecimal unitsPerDose, UUID householdId, String zone, boolean asNeeded) { }
    private record PriorDoseAction(UUID doseEventId, String action, String resultStatus, Instant actedAt, UUID actedBy, String note) { }
    private record MissedDose(UUID id, UUID householdId) { }
    public record DoseActionResult(UUID id, String status, Instant actedAt, UUID actedBy, String note) { }
}
