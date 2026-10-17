package com.khayaal.medicines;

import com.khayaal.doses.DoseEventGenerator;
import com.khayaal.household.HouseholdAuthorization;
import com.khayaal.identity.CurrentUser;
import com.khayaal.schedule.ScheduleExpander;
import com.khayaal.schedule.ScheduleRule;
import com.khayaal.schedule.ScheduleType;
import com.khayaal.stock.StockService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class MedicineController {
    private final JdbcClient jdbc; private final HouseholdAuthorization authorization; private final DoseEventGenerator generator; private final Clock clock; private final StockService stock;
    public MedicineController(JdbcClient jdbc,HouseholdAuthorization authorization,DoseEventGenerator generator,Clock clock,StockService stock){this.jdbc=jdbc;this.authorization=authorization;this.generator=generator;this.clock=clock;this.stock=stock;}

    @PostMapping("/people/{recipientId}/medicines") @ResponseStatus(HttpStatus.CREATED) @Transactional
    MedicineView create(@PathVariable UUID recipientId,@Valid @RequestBody MedicineRequest request,Authentication auth){
        Recipient recipient=recipient(recipientId); authorization.requireWriter(CurrentUser.require(auth).userId(),recipient.householdId()); validateQuarter(request.unitsPerDose());
        UUID medicine=UUID.randomUUID();
        jdbc.sql("insert into medicines(id,recipient_id,name,strength,form,units_per_dose,instructions,start_date,end_date,as_needed,active,lead_time_days) values (:id,:recipient,:name,:strength,:form,:units,:instructions,:start,:end,:prn,true,:lead)")
                .param("id",medicine).param("recipient",recipientId).param("name",request.name().trim()).param("strength",request.strength()).param("form",request.form())
                .param("units",request.unitsPerDose()).param("instructions",request.instructions()).param("start",request.startDate()).param("end",request.endDate())
                .param("prn",request.asNeeded()).param("lead",request.leadTimeDays()).update();
        if(!request.asNeeded()) saveSchedule(medicine,request.schedule(),recipient,request.startDate(),request.endDate(),clock.instant());
        return view(medicine);
    }

    @PostMapping("/households/{householdId}/medicines-with-purchase") @ResponseStatus(HttpStatus.CREATED) @Transactional
    MedicineWithPurchaseView createWithPurchase(@PathVariable UUID householdId,@Valid @RequestBody MedicineWithPurchaseRequest request,Authentication auth){
        UUID actor=CurrentUser.require(auth).userId(); authorization.requireWriter(actor,householdId);
        Recipient recipient=recipient(request.recipientId());
        if(!recipient.householdId().equals(householdId))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Recipient not found");
        MedicineView medicine=create(request.recipientId(),request.medicine(),auth);
        StockService.PurchaseResult purchase=stock.recordPurchase(medicine.id(),request.purchase().packCount(),request.purchase().unitsPerPack(),
                request.purchase().price(),request.purchase().pharmacy(),request.purchase().purchasedOn(),request.purchase().batchNumber(),
                request.purchase().expiryDate(),request.purchase().note(),actor);
        return new MedicineWithPurchaseView(medicine,purchase);
    }

    @PutMapping("/medicines/{medicineId}/schedule") @Transactional
    MedicineView replaceSchedule(@PathVariable UUID medicineId,@Valid @RequestBody ReplaceScheduleRequest request,Authentication auth){
        MedicineContext context=context(medicineId); authorization.requireWriter(CurrentUser.require(auth).userId(),context.householdId()); Instant effective=request.effectiveFrom();
        jdbc.sql("update schedule_rule_versions set effective_to=:effective where medicine_id=:medicine and effective_from<:effective and (effective_to is null or effective_to>:effective)").param("effective",Timestamp.from(effective)).param("medicine",medicineId).update();
        jdbc.sql("delete from dose_events where medicine_id=:medicine and status='PENDING' and scheduled_at>=:effective").param("medicine",medicineId).param("effective",Timestamp.from(effective)).update();
        saveSchedule(medicineId,request.schedule(),new Recipient(context.householdId(),context.zone()),context.startDate(),context.endDate(),effective); return view(medicineId);
    }

    @PostMapping("/medicines/schedule-preview")
    List<PreviewOccurrence> preview(@Valid @RequestBody SchedulePreviewRequest request){
        ZoneId zone=ZoneId.of(request.timezone()); ScheduleRule rule=toRule(request.schedule(),request.startDate(),request.endDate()); Instant start=request.from();
        return new ScheduleExpander().expand(rule,zone,start,start.plus(request.days(),java.time.temporal.ChronoUnit.DAYS)).stream().map(o->new PreviewOccurrence(o.scheduledAt(),o.scheduledLocal())).toList();
    }

    @GetMapping("/people/{recipientId}/medicines")
    List<MedicineView> list(@PathVariable UUID recipientId,Authentication auth){Recipient recipient=recipient(recipientId);authorization.requireMembership(CurrentUser.require(auth).userId(),recipient.householdId());
        return jdbc.sql("select id,name,strength,form,units_per_dose,instructions,start_date,end_date,as_needed,active,lead_time_days from medicines where recipient_id=:recipient order by name")
                .param("recipient",recipientId).query((rs,n)->map(rs)).list();}

    @PutMapping("/medicines/{medicineId}") @Transactional
    MedicineView update(@PathVariable UUID medicineId,@Valid @RequestBody MedicineDetailsRequest request,Authentication auth){
        MedicineContext context=context(medicineId); authorization.requireWriter(CurrentUser.require(auth).userId(),context.householdId()); validateQuarter(request.unitsPerDose());
        jdbc.sql("update medicines set name=:name,strength=:strength,form=:form,units_per_dose=:units,instructions=:instructions,end_date=:end,lead_time_days=:lead where id=:id")
                .param("name",request.name().trim()).param("strength",request.strength()).param("form",request.form()).param("units",request.unitsPerDose())
                .param("instructions",request.instructions()).param("end",request.endDate()).param("lead",request.leadTimeDays()).param("id",medicineId).update();
        return view(medicineId);
    }

    @DeleteMapping("/medicines/{medicineId}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
    void archive(@PathVariable UUID medicineId,Authentication auth){MedicineContext context=context(medicineId);authorization.requireWriter(CurrentUser.require(auth).userId(),context.householdId());jdbc.sql("update medicines set active=false where id=:id").param("id",medicineId).update();}

    private void saveSchedule(UUID medicine,ScheduleRequest request,Recipient recipient,LocalDate start,LocalDate end,Instant effective){
        ScheduleRule rule=toRule(request,start,end); UUID id=UUID.randomUUID();
        jdbc.sql("insert into schedule_rule_versions(id,medicine_id,rule_type,interval_days,anchor_date,effective_from) values (:id,:medicine,:type,:interval,:anchor,:effective)")
                .param("id",id).param("medicine",medicine).param("type",rule.type().name()).param("interval",rule.intervalDays()==0?null:rule.intervalDays()).param("anchor",rule.anchorDate()).param("effective",Timestamp.from(effective)).update();
        rule.localTimes().forEach(time->jdbc.sql("insert into schedule_rule_times(schedule_rule_id,local_time) values (:id,:time)").param("id",id).param("time",time).update());
        rule.daysOfWeek().forEach(day->jdbc.sql("insert into schedule_rule_days(schedule_rule_id,day_of_week) values (:id,:day)").param("id",id).param("day",day.getValue()).update());
        generator.generate(medicine,id,rule,ZoneId.of(recipient.zone()),effective);
    }
    private ScheduleRule toRule(ScheduleRequest request,LocalDate start,LocalDate end){return new ScheduleRule(request.type(),start,end,request.daysOfWeek()==null?Set.of():request.daysOfWeek(),request.intervalDays()==null?0:request.intervalDays(),request.anchorDate(),request.localTimes());}
    private Recipient recipient(UUID id){return jdbc.sql("select household_id,timezone from care_recipients where id=:id and archived=false").param("id",id).query((rs,n)->new Recipient(rs.getObject(1,UUID.class),rs.getString(2))).optional().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Recipient not found"));}
    private MedicineContext context(UUID id){return jdbc.sql("select r.household_id,r.timezone,m.start_date,m.end_date from medicines m join care_recipients r on r.id=m.recipient_id where m.id=:id for update").param("id",id).query((rs,n)->new MedicineContext(rs.getObject(1,UUID.class),rs.getString(2),rs.getDate(3).toLocalDate(),rs.getDate(4)==null?null:rs.getDate(4).toLocalDate())).optional().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}
    private MedicineView view(UUID id){return jdbc.sql("select id,name,strength,form,units_per_dose,instructions,start_date,end_date,as_needed,active,lead_time_days from medicines where id=:id").param("id",id).query((rs,n)->map(rs)).single();}
    private MedicineView map(java.sql.ResultSet rs)throws java.sql.SQLException{return new MedicineView(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4),rs.getBigDecimal(5),rs.getString(6),rs.getDate(7).toLocalDate(),rs.getDate(8)==null?null:rs.getDate(8).toLocalDate(),rs.getBoolean(9),rs.getBoolean(10),rs.getInt(11));}
    private void validateQuarter(BigDecimal value){if(value==null||value.signum()<=0||value.remainder(new BigDecimal("0.25")).signum()!=0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"unitsPerDose must use positive quarter-unit steps");}
    record MedicineRequest(@NotBlank @Size(max=150) String name,@Size(max=100) String strength,@NotBlank String form,@NotNull BigDecimal unitsPerDose,@Size(max=1000) String instructions,@NotNull LocalDate startDate,LocalDate endDate,boolean asNeeded,@Min(0) int leadTimeDays,ScheduleRequest schedule){public MedicineRequest{if(!asNeeded&&schedule==null)throw new IllegalArgumentException("A scheduled medicine requires a schedule");}}
    record MedicineDetailsRequest(@NotBlank @Size(max=150) String name,@Size(max=100) String strength,@NotBlank String form,@NotNull BigDecimal unitsPerDose,@Size(max=1000) String instructions,LocalDate endDate,@Min(0) int leadTimeDays) { }
    record InitialPurchaseRequest(@Min(1) int packCount,@NotNull @Positive BigDecimal unitsPerPack,@PositiveOrZero BigDecimal price,String pharmacy,@NotNull LocalDate purchasedOn,String batchNumber,LocalDate expiryDate,String note) { }
    record MedicineWithPurchaseRequest(@NotNull UUID recipientId,@NotNull @Valid MedicineRequest medicine,@NotNull @Valid InitialPurchaseRequest purchase) { }
    record MedicineWithPurchaseView(MedicineView medicine,StockService.PurchaseResult purchase) { }
    record ScheduleRequest(@NotNull ScheduleType type,List<LocalTime> localTimes,Set<DayOfWeek> daysOfWeek,Integer intervalDays,LocalDate anchorDate){public ScheduleRequest{localTimes=localTimes==null?List.of():List.copyOf(localTimes);}}
    record ReplaceScheduleRequest(@NotNull Instant effectiveFrom,@NotNull ScheduleRequest schedule) { }
    record SchedulePreviewRequest(@NotNull ScheduleRequest schedule,@NotBlank String timezone,@NotNull LocalDate startDate,LocalDate endDate,@NotNull Instant from,@Min(1) @Max(31) int days) { }
    record PreviewOccurrence(Instant scheduledAt,LocalDateTime scheduledLocal) { }
    record MedicineView(UUID id,String name,String strength,String form,BigDecimal unitsPerDose,String instructions,LocalDate startDate,LocalDate endDate,boolean asNeeded,boolean active,int leadTimeDays) { }
    private record Recipient(UUID householdId,String zone) { }
    private record MedicineContext(UUID householdId,String zone,LocalDate startDate,LocalDate endDate) { }
}
