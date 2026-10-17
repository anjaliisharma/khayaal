package com.khayaal.stock;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class StockService {
    private static final Duration PURCHASE_VOID_WINDOW = Duration.ofHours(24);
    private final JdbcClient jdbc;
    private final Clock clock;

    public StockService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public PurchaseResult recordPurchase(UUID medicineId, int packCount, BigDecimal unitsPerPack, BigDecimal price,
                                         String pharmacy, LocalDate purchasedOn, String batchNumber,
                                         LocalDate expiryDate, String note, UUID actorId) {
        if (packCount < 1) throw new IllegalArgumentException("packCount must be positive");
        UnitQuantity.requirePositiveWholeUnits(unitsPerPack, "unitsPerPack");
        BigDecimal totalUnits = UnitQuantity.requirePositiveQuarterUnits(unitsPerPack.multiply(BigDecimal.valueOf(packCount)), "totalUnits");
        UUID purchaseId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.sql("insert into purchases(id,medicine_id,pack_count,units_per_pack,total_units,price,pharmacy,purchased_on,batch_number,expiry_date,note,created_by,created_at) " +
                        "values (:id,:medicine,:packs,:perPack,:total,:price,:pharmacy,:purchasedOn,:batch,:expiry,:note,:actor,:now)")
                .param("id", purchaseId).param("medicine", medicineId).param("packs", packCount).param("perPack", unitsPerPack)
                .param("total", totalUnits).param("price", price).param("pharmacy", pharmacy).param("purchasedOn", purchasedOn)
                .param("batch", batchNumber).param("expiry", expiryDate).param("note", note).param("actor", actorId)
                .param("now", Timestamp.from(now)).update();
        jdbc.sql("insert into stock_lots(id,medicine_id,purchase_id,original_units,remaining_units,batch_number,expiry_date,purchased_at) " +
                        "values (:id,:medicine,:purchase,:units,:units,:batch,:expiry,:now)")
                .param("id", lotId).param("medicine", medicineId).param("purchase", purchaseId).param("units", totalUnits)
                .param("batch", batchNumber).param("expiry", expiryDate).param("now", Timestamp.from(now)).update();
        appendLedger(medicineId, lotId, null, purchaseId, totalUnits, "PURCHASE", note, actorId, now);
        jdbc.sql("update reorder_requests set status='RECEIVED',closed_at=:now where medicine_id=:medicine and status='ORDERED'")
                .param("now", Timestamp.from(now)).param("medicine", medicineId).update();
        return new PurchaseResult(purchaseId, lotId, totalUnits, balance(medicineId));
    }

    @Transactional
    public void consumeForDose(UUID medicineId, UUID doseEventId, BigDecimal units, LocalDate consumptionDate, UUID actorId) {
        BigDecimal unmet = UnitQuantity.requirePositiveQuarterUnits(units, "units");
        List<LotRow> lots = lockUsableLots(medicineId, consumptionDate);
        Instant now = clock.instant();
        for (LotRow lot : lots) {
            if (unmet.signum() == 0) break;
            BigDecimal allocated = lot.remaining().min(unmet);
            if (allocated.signum() == 0) continue;
            jdbc.sql("update stock_lots set remaining_units=remaining_units-:units where id=:id")
                    .param("units", allocated).param("id", lot.id()).update();
            appendLedger(medicineId, lot.id(), doseEventId, null, allocated.negate(), "DOSE_TAKEN", null, actorId, now);
            unmet = unmet.subtract(allocated);
        }
        if (unmet.signum() > 0) {
            jdbc.sql("insert into stock_shortages(id,medicine_id,dose_event_id,unmet_units,recorded_at) values (:id,:medicine,:dose,:units,:at) on conflict(dose_event_id) do nothing")
                    .param("id", UUID.randomUUID()).param("medicine", medicineId).param("dose", doseEventId)
                    .param("units", unmet).param("at", Timestamp.from(now)).update();
        }
    }

    @Transactional
    public void undoDose(UUID medicineId, UUID doseEventId, UUID actorId) {
        List<DoseDeduction> deductions = jdbc.sql("select stock_lot_id,delta from stock_ledger_entries where dose_event_id=:dose and reason='DOSE_TAKEN' order by created_at")
                .param("dose", doseEventId).query((rs, row) -> new DoseDeduction((UUID) rs.getObject(1), rs.getBigDecimal(2))).list();
        Instant now = clock.instant();
        for (DoseDeduction deduction : deductions) {
            BigDecimal restored = deduction.delta().abs();
            if (deduction.lotId() != null) jdbc.sql("update stock_lots set remaining_units=remaining_units+:units where id=:id")
                    .param("units", restored).param("id", deduction.lotId()).update();
            appendLedger(medicineId, deduction.lotId(), doseEventId, null, restored, "DOSE_UNDO", null, actorId, now);
        }
        jdbc.sql("delete from stock_shortages where dose_event_id=:dose").param("dose", doseEventId).update();
    }

    @Transactional
    public BigDecimal adjust(UUID medicineId, BigDecimal delta, String reason, String note, UUID actorId) {
        UnitQuantity.requirePositiveQuarterUnits(delta.abs(), "delta");
        if (!reason.equals("ADJUSTMENT") && !reason.equals("DISCARD")) throw new IllegalArgumentException("Unsupported stock reason");
        if (reason.equals("DISCARD") && delta.signum() > 0) delta = delta.negate();
        Instant now = clock.instant();
        if (delta.signum() > 0) {
            UUID lotId = UUID.randomUUID();
            jdbc.sql("insert into stock_lots(id,medicine_id,original_units,remaining_units,purchased_at) values (:id,:medicine,:units,:units,:at)")
                    .param("id", lotId).param("medicine", medicineId).param("units", delta).param("at", Timestamp.from(now)).update();
            appendLedger(medicineId, lotId, null, null, delta, reason, note, actorId, now);
        } else {
            BigDecimal remaining = delta.abs();
            for (LotRow lot : lockUsableLots(medicineId, LocalDate.now(clock))) {
                if (remaining.signum() == 0) break;
                BigDecimal removed = lot.remaining().min(remaining);
                jdbc.sql("update stock_lots set remaining_units=remaining_units-:units where id=:id")
                        .param("units", removed).param("id", lot.id()).update();
                appendLedger(medicineId, lot.id(), null, null, removed.negate(), reason, note, actorId, now);
                remaining = remaining.subtract(removed);
            }
            if (remaining.signum() > 0)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot remove more units than are currently in stock");
        }
        return balance(medicineId);
    }

    @Transactional
    public void expireLots(LocalDate today) {
        List<LotRow> expired = jdbc.sql("select id,medicine_id,remaining_units from stock_lots where expiry_date<:today and remaining_units>0 for update")
                .param("today", today).query((rs, row) -> new LotRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getBigDecimal(3))).list();
        Instant now = clock.instant();
        expired.forEach(lot -> {
            jdbc.sql("update stock_lots set remaining_units=0 where id=:id").param("id", lot.id()).update();
            appendLedger(lot.medicineId(), lot.id(), null, null, lot.remaining().negate(), "EXPIRED", null, null, now);
        });
    }

    @Transactional
    public void voidPurchase(UUID purchaseId, UUID actorId) {
        PurchaseForVoid purchase = jdbc.sql("select p.medicine_id,p.total_units,p.created_at,l.id,l.original_units,l.remaining_units from purchases p join stock_lots l on l.purchase_id=p.id where p.id=:id and p.voided_at is null for update")
                .param("id", purchaseId).query((rs, row) -> new PurchaseForVoid(rs.getObject(1, UUID.class), rs.getBigDecimal(2),
                        rs.getObject(3, java.time.OffsetDateTime.class).toInstant(), rs.getObject(4, UUID.class), rs.getBigDecimal(5), rs.getBigDecimal(6)))
                .optional().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase not found"));
        Instant now = clock.instant();
        if (purchase.createdAt().plus(PURCHASE_VOID_WINDOW).isBefore(now)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Purchase void window has elapsed");
        if (purchase.original().compareTo(purchase.remaining()) != 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Consumed purchases require a stock adjustment");
        appendLedger(purchase.medicineId(), purchase.lotId(), null, purchaseId, purchase.total().negate(), "PURCHASE_VOID", null, actorId, now);
        jdbc.sql("delete from stock_lots where id=:id").param("id", purchase.lotId()).update();
        jdbc.sql("update purchases set voided_at=:now,voided_by=:actor where id=:id")
                .param("now", Timestamp.from(now)).param("actor", actorId).param("id", purchaseId).update();
    }

    public BigDecimal balance(UUID medicineId) {
        return jdbc.sql("select coalesce(sum(delta),0) from stock_ledger_entries where medicine_id=:medicine")
                .param("medicine", medicineId).query(BigDecimal.class).single().max(BigDecimal.ZERO);
    }

    private List<LotRow> lockUsableLots(UUID medicineId, LocalDate date) {
        return jdbc.sql("select id,medicine_id,remaining_units from stock_lots where medicine_id=:medicine and remaining_units>0 and (expiry_date is null or expiry_date>=:date) " +
                        "order by expiry_date nulls last,purchased_at,id for update")
                .param("medicine", medicineId).param("date", date)
                .query((rs, row) -> new LotRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getBigDecimal(3))).list();
    }

    private void appendLedger(UUID medicine, UUID lot, UUID dose, UUID purchase, BigDecimal delta, String reason,
                              String note, UUID actor, Instant at) {
        jdbc.sql("insert into stock_ledger_entries(id,medicine_id,stock_lot_id,dose_event_id,purchase_id,delta,reason,note,actor_id,created_at) " +
                        "values (:id,:medicine,:lot,:dose,:purchase,:delta,:reason,:note,:actor,:at)")
                .param("id", UUID.randomUUID()).param("medicine", medicine).param("lot", lot).param("dose", dose)
                .param("purchase", purchase).param("delta", delta).param("reason", reason).param("note", note)
                .param("actor", actor).param("at", Timestamp.from(at)).update();
    }


    private record LotRow(UUID id, UUID medicineId, BigDecimal remaining) { }
    private record DoseDeduction(UUID lotId, BigDecimal delta) { }
    private record PurchaseForVoid(UUID medicineId, BigDecimal total, Instant createdAt, UUID lotId, BigDecimal original, BigDecimal remaining) { }
    public record PurchaseResult(UUID purchaseId, UUID stockLotId, BigDecimal addedUnits, BigDecimal currentBalance) { }
}
