package com.khayaal.notify;

import com.khayaal.doses.DoseActionService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.sql.Timestamp;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationPlanner {
    private final JdbcClient jdbc;private final NotificationOutbox outbox;private final DoseActionService doseActions;
    private final NotificationProperties properties;private final Clock clock;
    private final Timer plannerTimer;
    public NotificationPlanner(JdbcClient jdbc,NotificationOutbox outbox,DoseActionService doseActions,NotificationProperties properties,Clock clock,MeterRegistry metrics){
        this.jdbc=jdbc;this.outbox=outbox;this.doseActions=doseActions;this.properties=properties;this.clock=clock;
        this.plannerTimer=metrics.timer("khayaal.notifications.planner");
    }

    @Scheduled(fixedRateString="${app.notifications.planner-rate-ms:30000}",initialDelayString="${app.notifications.initial-delay-ms:10000}")
    public int plan(){return plannerTimer.record(() -> planAt(clock.instant()));}

    public int planAt(Instant now){
        doseActions.markOverduePendingDosesMissed(now);
        int inserted=0;
        inserted+=planDoseType("DOSE_DUE","d.status='PENDING' and d.scheduled_at<=:cutoff",now,now,"Dose reminder");
        inserted+=planDoseType("DOSE_FOLLOWUP","d.status='PENDING' and d.scheduled_at<=:cutoff",now.minusSeconds(properties.followUpMinutes()*60L),now,"Dose follow-up");
        inserted+=planDoseType("DOSE_MISSED","d.status='MISSED' and d.scheduled_at<=:cutoff",now,now,"Missed dose");
        inserted+=planStockAlerts(now);
        inserted+=planExpiryAlerts(now);
        return inserted;
    }

    private int planDoseType(String type,String condition,Instant cutoff,Instant now,String subject){
        List<DoseCandidate> candidates=jdbc.sql("select d.id,d.medicine_id,m.name,r.name,r.household_id,hm.user_id,coalesce(p.email_enabled,true),"+
                        "coalesce(p.telegram_enabled,false),coalesce(p.discreet_mode,false),coalesce(p.dose_missed_enabled,true),ta.chat_id " +
                        "from dose_events d join medicines m on m.id=d.medicine_id join care_recipients r on r.id=m.recipient_id " +
                        "join household_memberships hm on hm.household_id=r.household_id left join notification_preferences p on p.user_id=hm.user_id " +
                        "left join telegram_accounts ta on ta.user_id=hm.user_id and ta.linked_at is not null where "+condition)
                .param("cutoff",Timestamp.from(cutoff)).query((rs,row)->new DoseCandidate(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),
                        rs.getString(3),rs.getString(4),rs.getObject(5,UUID.class),rs.getObject(6,UUID.class),rs.getBoolean(7),rs.getBoolean(8),
                        rs.getBoolean(9),rs.getBoolean(10),rs.getString(11))).list();
        int inserted=0;
        for(DoseCandidate candidate:candidates){
            if(type.equals("DOSE_MISSED")&&!candidate.missedEnabled())continue;
            String body=message(type,candidate);
            String link=properties.applicationUrl()+"/?dose="+candidate.doseId();
            if(candidate.emailEnabled())inserted+=enqueue(candidate,"EMAIL",type,subject,body,link,now);
            if(candidate.telegramEnabled()&&candidate.telegramChatId()!=null)inserted+=enqueue(candidate,"TELEGRAM",type,subject,body,link,now);
        }
        return inserted;
    }

    private int enqueue(DoseCandidate candidate,String channel,String type,String subject,String body,String link,Instant now){
        String dedupe=type+":"+candidate.doseId()+":"+candidate.userId()+":"+channel;
        return outbox.enqueue(new NotificationOutbox.PlannedNotification(candidate.userId(),candidate.householdId(),candidate.doseId(),candidate.medicineId(),
                channel,type,"HIGH",subject,body,link,dedupe,now,now));
    }

    private String message(String type,DoseCandidate candidate){
        String detail=candidate.discreet()?candidate.recipientName()+"'s scheduled medicines":candidate.recipientName()+"'s "+candidate.medicineName();
        return switch(type){case "DOSE_DUE"->"Time for "+detail+".";case "DOSE_FOLLOWUP"->"Please confirm "+detail+".";
            case "DOSE_MISSED"->"A scheduled dose for "+detail+" was not confirmed.";default->throw new IllegalArgumentException(type);};
    }

    private int planStockAlerts(Instant now){
        List<StockCandidate> candidates=jdbc.sql("select m.id,m.name,m.units_per_dose,m.lead_time_days,r.name,r.timezone,r.household_id,hm.user_id,"+
                        "coalesce(p.email_enabled,true),coalesce(p.telegram_enabled,false),coalesce(p.discreet_mode,false),p.quiet_hours_start,p.quiet_hours_end,"+
                        "coalesce(p.timezone,r.timezone),ta.chat_id,coalesce((select sum(delta) from stock_ledger_entries le where le.medicine_id=m.id),0) " +
                        "from medicines m join care_recipients r on r.id=m.recipient_id join household_memberships hm on hm.household_id=r.household_id " +
                        "left join notification_preferences p on p.user_id=hm.user_id left join telegram_accounts ta on ta.user_id=hm.user_id and ta.linked_at is not null " +
                        "where m.active=true and m.as_needed=false")
                .query((rs,row)->new StockCandidate(rs.getObject(1,UUID.class),rs.getString(2),rs.getBigDecimal(3),rs.getInt(4),rs.getString(5),rs.getString(6),
                        rs.getObject(7,UUID.class),rs.getObject(8,UUID.class),rs.getBoolean(9),rs.getBoolean(10),rs.getBoolean(11),rs.getObject(12,LocalTime.class),
                        rs.getObject(13,LocalTime.class),rs.getString(14),rs.getString(15),rs.getBigDecimal(16))).list();
        int inserted=0;
        for(StockCandidate candidate:candidates){
            List<LocalDate> futureDates=jdbc.sql("select scheduled_local::date from dose_events where medicine_id=:medicine and scheduled_at>=:now order by scheduled_at")
                    .param("medicine",candidate.medicineId()).param("now",Timestamp.from(now)).query(LocalDate.class).list();
            int covered=candidate.balance().max(BigDecimal.ZERO).divide(candidate.unitsPerDose(),0,java.math.RoundingMode.DOWN).intValue();
            if(covered>=futureDates.size())continue;
            LocalDate runOut=futureDates.get(covered);LocalDate today=now.atZone(ZoneId.of(candidate.recipientZone())).toLocalDate();
            long days=java.time.temporal.ChronoUnit.DAYS.between(today,runOut);if(days>candidate.leadTimeDays())continue;
            boolean critical=days<=1;
            if(!critical&&hasActiveReorder(candidate.medicineId(),today))continue;
            String type=critical?"OUT_OF_STOCK_CRITICAL":"LOW_STOCK";String priority=critical?"HIGH":"NORMAL";
            String detail=candidate.discreet()?candidate.recipientName()+"'s medicine":candidate.medicineName();
            String body=critical?detail+" may not cover tomorrow's scheduled dose.":detail+" is expected to run out on "+runOut+".";
            Instant available=critical?now:afterQuietHours(now,candidate.preferenceZone(),candidate.quietStart(),candidate.quietEnd());
            String suffix=type+":"+candidate.medicineId()+":"+runOut+":"+today;
            if(candidate.emailEnabled())inserted+=enqueueStock(candidate,"EMAIL",type,priority,body,suffix,available,now);
            if(candidate.telegramEnabled()&&candidate.telegramChatId()!=null)inserted+=enqueueStock(candidate,"TELEGRAM",type,priority,body,suffix,available,now);
        }
        return inserted;
    }

    private int planExpiryAlerts(Instant now){
        List<ExpiryCandidate> candidates=jdbc.sql("select l.id,m.id,m.name,r.name,r.household_id,hm.user_id,l.expiry_date,m.lead_time_days,r.timezone,"+
                        "coalesce(p.email_enabled,true),coalesce(p.telegram_enabled,false),coalesce(p.discreet_mode,false),p.quiet_hours_start,p.quiet_hours_end,coalesce(p.timezone,r.timezone),ta.chat_id " +
                        "from stock_lots l join medicines m on m.id=l.medicine_id join care_recipients r on r.id=m.recipient_id join household_memberships hm on hm.household_id=r.household_id " +
                        "left join notification_preferences p on p.user_id=hm.user_id left join telegram_accounts ta on ta.user_id=hm.user_id and ta.linked_at is not null " +
                        "where l.remaining_units>0 and l.expiry_date is not null")
                .query((rs,row)->new ExpiryCandidate(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getString(4),rs.getObject(5,UUID.class),
                        rs.getObject(6,UUID.class),rs.getObject(7,LocalDate.class),rs.getInt(8),rs.getString(9),rs.getBoolean(10),rs.getBoolean(11),rs.getBoolean(12),
                        rs.getObject(13,LocalTime.class),rs.getObject(14,LocalTime.class),rs.getString(15),rs.getString(16))).list();
        int inserted=0;
        for(ExpiryCandidate c:candidates){LocalDate today=now.atZone(ZoneId.of(c.recipientZone())).toLocalDate();long days=java.time.temporal.ChronoUnit.DAYS.between(today,c.expiryDate());
            if(days<0||days>c.leadDays())continue;String detail=c.discreet()?c.recipientName()+"'s medicine":c.medicineName();String body=detail+" has stock expiring on "+c.expiryDate()+".";
            Instant available=afterQuietHours(now,c.preferenceZone(),c.quietStart(),c.quietEnd());String suffix="EXPIRY:"+c.lotId()+":"+c.expiryDate();
            if(c.emailEnabled())inserted+=outbox.enqueue(new NotificationOutbox.PlannedNotification(c.userId(),c.householdId(),null,c.medicineId(),"EMAIL","EXPIRY","NORMAL","Stock expiry",body,properties.applicationUrl()+"/stock",suffix+":"+c.userId()+":EMAIL",available,now));
            if(c.telegramEnabled()&&c.telegramChatId()!=null)inserted+=outbox.enqueue(new NotificationOutbox.PlannedNotification(c.userId(),c.householdId(),null,c.medicineId(),"TELEGRAM","EXPIRY","NORMAL","Stock expiry",body,properties.applicationUrl()+"/stock",suffix+":"+c.userId()+":TELEGRAM",available,now));}
        return inserted;
    }

    private int enqueueStock(StockCandidate c,String channel,String type,String priority,String body,String suffix,Instant available,Instant now){
        return outbox.enqueue(new NotificationOutbox.PlannedNotification(c.userId(),c.householdId(),null,c.medicineId(),channel,type,priority,type.equals("LOW_STOCK")?"Low stock":"Critical stock alert",body,
                properties.applicationUrl()+"/stock",suffix+":"+c.userId()+":"+channel,available,now));
    }
    private boolean hasActiveReorder(UUID medicine,LocalDate today){return jdbc.sql("select count(*) from reorder_requests where medicine_id=:medicine and status='ORDERED' and expected_by>=:today")
            .param("medicine",medicine).param("today",today).query(Long.class).single()>0;}
    private Instant afterQuietHours(Instant now,String zoneName,LocalTime start,LocalTime end){
        if(start==null||end==null||start.equals(end))return now;ZoneId zone=ZoneId.of(zoneName);var local=now.atZone(zone);boolean quiet=start.isBefore(end)?!local.toLocalTime().isBefore(start)&&local.toLocalTime().isBefore(end):!local.toLocalTime().isBefore(start)||local.toLocalTime().isBefore(end);
        if(!quiet)return now;LocalDate endDate=start.isBefore(end)||local.toLocalTime().isBefore(end)?local.toLocalDate():local.toLocalDate().plusDays(1);return ZonedDateTime.of(endDate,end,zone).toInstant();
    }

    private record DoseCandidate(UUID doseId,UUID medicineId,String medicineName,String recipientName,UUID householdId,UUID userId,
                                 boolean emailEnabled,boolean telegramEnabled,boolean discreet,boolean missedEnabled,String telegramChatId) { }
    private record StockCandidate(UUID medicineId,String medicineName,BigDecimal unitsPerDose,int leadTimeDays,String recipientName,String recipientZone,
                                  UUID householdId,UUID userId,boolean emailEnabled,boolean telegramEnabled,boolean discreet,LocalTime quietStart,LocalTime quietEnd,
                                  String preferenceZone,String telegramChatId,BigDecimal balance) { }
    private record ExpiryCandidate(UUID lotId,UUID medicineId,String medicineName,String recipientName,UUID householdId,UUID userId,LocalDate expiryDate,int leadDays,
                                   String recipientZone,boolean emailEnabled,boolean telegramEnabled,boolean discreet,LocalTime quietStart,LocalTime quietEnd,String preferenceZone,String telegramChatId) { }
}
