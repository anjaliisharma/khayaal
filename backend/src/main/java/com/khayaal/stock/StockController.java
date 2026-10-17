package com.khayaal.stock;

import com.khayaal.forecast.ForecastRequest;
import com.khayaal.forecast.RunOutForecast;
import com.khayaal.forecast.RunOutForecastEngine;
import com.khayaal.household.HouseholdAuthorization;
import com.khayaal.identity.CurrentUser;
import com.khayaal.medicines.SchedulePersistence;
import com.khayaal.schedule.ScheduleRule;
import com.khayaal.schedule.ScheduleType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URI;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class StockController {
    private final JdbcClient jdbc;
    private final StockService stock;
    private final HouseholdAuthorization authorization;
    private final Clock clock;
    private final RunOutForecastEngine forecasts = new RunOutForecastEngine();

    public StockController(JdbcClient jdbc, StockService stock, HouseholdAuthorization authorization, Clock clock) {
        this.jdbc = jdbc;
        this.stock = stock;
        this.authorization = authorization;
        this.clock = clock;
    }

    @PostMapping("/medicines/{medicineId}/purchases") @ResponseStatus(HttpStatus.CREATED)
    StockService.PurchaseResult purchase(@PathVariable UUID medicineId, @Valid @RequestBody PurchaseRequest request,
                                         Authentication authentication) {
        MedicineContext context = medicine(medicineId);
        UUID actor = CurrentUser.require(authentication).userId();
        authorization.requireWriter(actor, context.householdId());
        return stock.recordPurchase(medicineId, request.packCount(), request.unitsPerPack(), request.price(), request.pharmacy(),
                request.purchasedOn(), request.batchNumber(), request.expiryDate(), request.note(), actor);
    }

    @DeleteMapping("/purchases/{purchaseId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void voidPurchase(@PathVariable UUID purchaseId, Authentication authentication) {
        UUID household = jdbc.sql("select r.household_id from purchases p join medicines m on m.id=p.medicine_id join care_recipients r on r.id=m.recipient_id where p.id=:id")
                .param("id", purchaseId).query(UUID.class).optional().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase not found"));
        UUID actor = CurrentUser.require(authentication).userId(); authorization.requireWriter(actor, household);
        stock.voidPurchase(purchaseId, actor);
    }

    @PostMapping("/medicines/{medicineId}/stock/adjust")
    BalanceView adjust(@PathVariable UUID medicineId, @Valid @RequestBody StockChangeRequest request, Authentication authentication) {
        return changeStock(medicineId, request.units(), "ADJUSTMENT", request.note(), authentication);
    }

    @PostMapping("/medicines/{medicineId}/stock/discard")
    BalanceView discard(@PathVariable UUID medicineId, @Valid @RequestBody StockChangeRequest request, Authentication authentication) {
        return changeStock(medicineId, request.units().abs().negate(), "DISCARD", request.note(), authentication);
    }

    @GetMapping("/medicines/{medicineId}/stock")
    StockView getStock(@PathVariable UUID medicineId, Authentication authentication) {
        MedicineContext context = medicine(medicineId);
        authorization.requireMembership(CurrentUser.require(authentication).userId(), context.householdId());
        List<LotView> lots = lots(medicineId);
        return new StockView(medicineId, stock.balance(medicineId), lots, forecast(context, lots));
    }

    @GetMapping("/stock/overview")
    List<StockOverview> overview(Authentication authentication) {
        UUID user = CurrentUser.require(authentication).userId();
        List<OverviewContext> medicines = jdbc.sql("select m.id,m.name,r.name,r.household_id,r.timezone,m.units_per_dose,m.start_date,m.end_date,m.as_needed,m.lead_time_days," +
                        "s.id,s.rule_type,s.interval_days,s.anchor_date,greatest(coalesce((select sum(le.delta) from stock_ledger_entries le where le.medicine_id=m.id),0),0),rr.status,rr.expected_by " +
                        "from medicines m join care_recipients r on r.id=m.recipient_id join household_memberships hm on hm.household_id=r.household_id " +
                        "left join schedule_rule_versions s on s.medicine_id=m.id and s.effective_to is null " +
                        "left join lateral (select status,expected_by from reorder_requests where medicine_id=m.id and status='ORDERED' order by created_at desc limit 1) rr on true " +
                        "where hm.user_id=:user and m.active=true order by r.name,m.name")
                .param("user", user).query((rs, row) -> new OverviewContext(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        new MedicineContext(rs.getObject(4, UUID.class), rs.getString(5), rs.getBigDecimal(6), rs.getObject(7, LocalDate.class),
                                rs.getObject(8, LocalDate.class), rs.getBoolean(9), rs.getInt(10), rs.getObject(11, UUID.class), rs.getString(12),
                                (Integer) rs.getObject(13), rs.getObject(14, LocalDate.class)), rs.getBigDecimal(15), rs.getString(16),
                        rs.getObject(17, LocalDate.class))).list();
        Map<UUID,List<LotView>> lotsByMedicine = new HashMap<>();
        jdbc.sql("select l.medicine_id,l.id,l.purchase_id,l.remaining_units,l.expiry_date,l.batch_number,l.purchased_at from stock_lots l join medicines m on m.id=l.medicine_id join care_recipients r on r.id=m.recipient_id join household_memberships hm on hm.household_id=r.household_id where hm.user_id=:user order by l.expiry_date nulls last,l.purchased_at")
                .param("user", user).query((rs, row) -> new MedicineLot(rs.getObject(1, UUID.class), new LotView(rs.getObject(2, UUID.class),
                        rs.getObject(3, UUID.class), rs.getBigDecimal(4), rs.getObject(5, LocalDate.class), rs.getString(6), rs.getObject(7, OffsetDateTime.class).toInstant())))
                .list().forEach(item -> lotsByMedicine.computeIfAbsent(item.medicineId(), ignored -> new ArrayList<>()).add(item.lot()));
        Map<UUID,List<LocalTime>> timesByRule = new HashMap<>();
        jdbc.sql("select t.schedule_rule_id,t.local_time from schedule_rule_times t join schedule_rule_versions s on s.id=t.schedule_rule_id join medicines m on m.id=s.medicine_id join care_recipients r on r.id=m.recipient_id join household_memberships hm on hm.household_id=r.household_id where hm.user_id=:user and s.effective_to is null")
                .param("user", user).query((rs, row) -> new RuleTime(rs.getObject(1, UUID.class), rs.getObject(2, LocalTime.class)))
                .list().forEach(item -> timesByRule.computeIfAbsent(item.ruleId(), ignored -> new ArrayList<>()).add(item.time()));
        Map<UUID,Set<DayOfWeek>> daysByRule = new HashMap<>();
        jdbc.sql("select d.schedule_rule_id,d.day_of_week from schedule_rule_days d join schedule_rule_versions s on s.id=d.schedule_rule_id join medicines m on m.id=s.medicine_id join care_recipients r on r.id=m.recipient_id join household_memberships hm on hm.household_id=r.household_id where hm.user_id=:user and s.effective_to is null")
                .param("user", user).query((rs, row) -> new RuleDay(rs.getObject(1, UUID.class), DayOfWeek.of(rs.getInt(2))))
                .list().forEach(item -> daysByRule.computeIfAbsent(item.ruleId(), ignored -> new HashSet<>()).add(item.day()));
        return medicines.stream().map(item -> new StockOverview(item.medicineId(), item.medicineName(), item.recipientName(), item.balance(),
                overviewForecast(item.context(), lotsByMedicine.getOrDefault(item.medicineId(), List.of()), timesByRule, daysByRule),
                item.reorderStatus(), item.expectedBy())).toList();
    }

    @GetMapping("/households/{householdId}/reorder-list")
    List<ReorderItem> reorderList(@PathVariable UUID householdId, @RequestParam(defaultValue="30") @Min(1) @Max(365) int days,
                                  Authentication authentication) {
        authorization.requireMembership(CurrentUser.require(authentication).userId(), householdId);
        return jdbc.sql("select m.id,m.name,m.strength,m.units_per_dose,m.lead_time_days,coalesce(sum(l.remaining_units),0) from medicines m " +
                        "join care_recipients r on r.id=m.recipient_id left join stock_lots l on l.medicine_id=m.id and (l.expiry_date is null or l.expiry_date>=current_date) " +
                        "where r.household_id=:household and m.active=true group by m.id having coalesce(sum(l.remaining_units),0)<=m.units_per_dose*m.lead_time_days order by m.name")
                .param("household", householdId).query((rs, row) -> new ReorderItem(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getBigDecimal(6), rs.getBigDecimal(4).multiply(BigDecimal.valueOf(days)).subtract(rs.getBigDecimal(6)).max(BigDecimal.ZERO))).list();
    }

    @PostMapping("/medicines/{medicineId}/reorder-requests") @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    ReorderRequestView reorder(@PathVariable UUID medicineId, @Valid @RequestBody ReorderRequest request, Authentication authentication) {
        MedicineContext context = medicine(medicineId); UUID actor = CurrentUser.require(authentication).userId();
        authorization.requireWriter(actor, context.householdId()); UUID id = UUID.randomUUID(); Instant now = clock.instant();
        ActiveReorder existing = activeReorder(medicineId);
        if (existing != null) return new ReorderRequestView(existing.id(), medicineId, existing.expectedBy(), existing.status());
        jdbc.sql("insert into reorder_requests(id,medicine_id,expected_by,status,created_by,created_at) values (:id,:medicine,:expected,'ORDERED',:actor,:now)")
                .param("id", id).param("medicine", medicineId).param("expected", request.expectedBy()).param("actor", actor).param("now", Timestamp.from(now)).update();
        return new ReorderRequestView(id, medicineId, request.expectedBy(), "ORDERED");
    }

    @GetMapping("/households/{householdId}/pharmacy-link")
    PharmacyLink pharmacyLink(@PathVariable UUID householdId, Authentication authentication) {
        authorization.requireMembership(CurrentUser.require(authentication).userId(), householdId);
        return new PharmacyLink(jdbc.sql("select pharmacy_url_template from households where id=:id").param("id", householdId).query(String.class).optional().orElse(null));
    }

    @PutMapping("/households/{householdId}/pharmacy-link")
    PharmacyLink pharmacyLink(@PathVariable UUID householdId, @Valid @RequestBody PharmacyLink request, Authentication authentication) {
        authorization.requireOwner(CurrentUser.require(authentication).userId(), householdId);
        if (request.urlTemplate() != null) {
            if (!request.urlTemplate().contains("{name}")) throw new IllegalArgumentException("Pharmacy URL must contain {name}");
            URI uri = URI.create(request.urlTemplate().replace("{name}", "medicine"));
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())))
                throw new IllegalArgumentException("Pharmacy URL must use http or https");
        }
        jdbc.sql("update households set pharmacy_url_template=:url where id=:id").param("url", request.urlTemplate()).param("id", householdId).update();
        return request;
    }

    private BalanceView changeStock(UUID medicineId, BigDecimal delta, String reason, String note, Authentication authentication) {
        MedicineContext context = medicine(medicineId); UUID actor = CurrentUser.require(authentication).userId();
        authorization.requireWriter(actor, context.householdId());
        return new BalanceView(stock.adjust(medicineId, delta, reason, note, actor));
    }

    private RunOutForecast forecast(MedicineContext medicine, List<LotView> lots) {
        if (medicine.asNeeded() || medicine.ruleId() == null) return null;
        var rule = SchedulePersistence.loadRule(jdbc, medicine.ruleId(), medicine.startDate(), medicine.endDate(), medicine.ruleType(), medicine.intervalDays(), medicine.anchorDate());
        List<StockLot> domainLots = lots.stream().map(l -> new StockLot(l.id().toString(), l.remainingUnits(), l.expiryDate(), l.purchasedAt())).toList();
        return forecasts.forecast(new ForecastRequest(rule, ZoneId.of(medicine.zone()), domainLots, medicine.unitsPerDose(), clock.instant(), BigDecimal.ONE, medicine.leadTimeDays()));
    }

    private RunOutForecast overviewForecast(MedicineContext medicine, List<LotView> lots, Map<UUID,List<LocalTime>> times,
                                             Map<UUID,Set<DayOfWeek>> days) {
        if (medicine.asNeeded() || medicine.ruleId() == null) return null;
        ScheduleRule rule = new ScheduleRule(ScheduleType.valueOf(medicine.ruleType()), medicine.startDate(), medicine.endDate(),
                days.getOrDefault(medicine.ruleId(), Set.of()), medicine.intervalDays() == null ? 0 : medicine.intervalDays(),
                medicine.anchorDate(), times.getOrDefault(medicine.ruleId(), List.of()));
        List<StockLot> domainLots = lots.stream().map(l -> new StockLot(l.id().toString(), l.remainingUnits(), l.expiryDate(), l.purchasedAt())).toList();
        return forecasts.forecast(new ForecastRequest(rule, ZoneId.of(medicine.zone()), domainLots, medicine.unitsPerDose(), clock.instant(), BigDecimal.ONE, medicine.leadTimeDays()));
    }

    private List<LotView> lots(UUID medicineId) {
        return jdbc.sql("select id,purchase_id,remaining_units,expiry_date,batch_number,purchased_at from stock_lots where medicine_id=:medicine order by expiry_date nulls last,purchased_at")
                .param("medicine", medicineId).query((rs, row) -> new LotView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getBigDecimal(3),
                        rs.getObject(4, LocalDate.class), rs.getString(5), rs.getObject(6, java.time.OffsetDateTime.class).toInstant())).list();
    }

    private ActiveReorder activeReorder(UUID medicineId) {
        return jdbc.sql("select id,expected_by,status from reorder_requests where medicine_id=:medicine and status='ORDERED' order by created_at desc limit 1")
                .param("medicine", medicineId)
                .query((rs, row) -> new ActiveReorder(rs.getObject(1, UUID.class), rs.getObject(2, LocalDate.class), rs.getString(3)))
                .optional().orElse(null);
    }

    private MedicineContext medicine(UUID id) {
        return jdbc.sql("select r.household_id,r.timezone,m.units_per_dose,m.start_date,m.end_date,m.as_needed,m.lead_time_days,s.id,s.rule_type,s.interval_days,s.anchor_date " +
                        "from medicines m join care_recipients r on r.id=m.recipient_id left join schedule_rule_versions s on s.medicine_id=m.id and s.effective_to is null where m.id=:id")
                .param("id", id).query((rs, row) -> new MedicineContext(rs.getObject(1, UUID.class), rs.getString(2), rs.getBigDecimal(3),
                        rs.getObject(4, LocalDate.class), rs.getObject(5, LocalDate.class), rs.getBoolean(6), rs.getInt(7),
                        rs.getObject(8, UUID.class), rs.getString(9), (Integer) rs.getObject(10), rs.getObject(11, LocalDate.class)))
                .optional().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medicine not found"));
    }

    record PurchaseRequest(@Min(1) int packCount,@NotNull @Positive BigDecimal unitsPerPack,@PositiveOrZero BigDecimal price,String pharmacy,
                           @NotNull LocalDate purchasedOn,String batchNumber,LocalDate expiryDate,String note) { }
    record StockChangeRequest(@NotNull BigDecimal units,String note) { }
    record BalanceView(BigDecimal balance) { }
    record LotView(UUID id,UUID purchaseId,BigDecimal remainingUnits,LocalDate expiryDate,String batchNumber,Instant purchasedAt) { }
    record StockView(UUID medicineId,BigDecimal balance,List<LotView> lots,RunOutForecast forecast) { }
    record StockOverview(UUID medicineId,String medicineName,String recipientName,BigDecimal balance,RunOutForecast forecast,
                         String reorderStatus,LocalDate expectedBy) { }
    record ReorderItem(UUID medicineId,String name,String strength,BigDecimal balance,BigDecimal unitsNeeded) { }
    record ReorderRequest(@NotNull LocalDate expectedBy) { }
    record ReorderRequestView(UUID id,UUID medicineId,LocalDate expectedBy,String status) { }
    private record ActiveReorder(UUID id,LocalDate expectedBy,String status) { }
    private record OverviewContext(UUID medicineId,String medicineName,String recipientName,MedicineContext context,BigDecimal balance,
                                   String reorderStatus,LocalDate expectedBy) { }
    private record MedicineLot(UUID medicineId,LotView lot) { }
    private record RuleTime(UUID ruleId,LocalTime time) { }
    private record RuleDay(UUID ruleId,DayOfWeek day) { }
    record PharmacyLink(@Size(max=1000) String urlTemplate) { }
    private record MedicineContext(UUID householdId,String zone,BigDecimal unitsPerDose,LocalDate startDate,LocalDate endDate,
                                   boolean asNeeded,int leadTimeDays,UUID ruleId,String ruleType,Integer intervalDays,LocalDate anchorDate) { }
}
