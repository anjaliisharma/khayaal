package com.khayaal.notify;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class NotificationOutbox {
    private final JdbcClient jdbc;
    public NotificationOutbox(JdbcClient jdbc){this.jdbc=jdbc;}

    public int enqueue(PlannedNotification notification){
        return jdbc.sql("insert into notifications(id,user_id,household_id,dose_event_id,medicine_id,channel,notification_type,priority,subject,body,deep_link,dedupe_key,status,next_attempt_at,created_at) " +
                        "values (:id,:user,:household,:dose,:medicine,:channel,:type,:priority,:subject,:body,:link,:dedupe,'PENDING',:available,:created) on conflict(dedupe_key) do nothing")
                .param("id",UUID.randomUUID()).param("user",notification.userId()).param("household",notification.householdId())
                .param("dose",notification.doseEventId()).param("medicine",notification.medicineId()).param("channel",notification.channel())
                .param("type",notification.type()).param("priority",notification.priority()).param("subject",notification.subject())
                .param("body",notification.body()).param("link",notification.deepLink()).param("dedupe",notification.dedupeKey())
                .param("available",Timestamp.from(notification.availableAt())).param("created",Timestamp.from(notification.createdAt())).update();
    }

    @Transactional
    public List<ClaimedNotification> claim(String workerId,int batchSize,Instant now){
        Instant stale=now.minusSeconds(300);
        return jdbc.sql("with candidates as (select id from notifications where ((status in ('PENDING','FAILED') and next_attempt_at<=:now) " +
                        "or (status='PROCESSING' and claimed_at<=:stale)) order by case priority when 'HIGH' then 0 else 1 end,next_attempt_at,created_at " +
                        "for update skip locked limit :limit) update notifications n set status='PROCESSING',claimed_at=:now,claimed_by=:worker " +
                        "from candidates c where n.id=c.id returning n.id,n.user_id,n.dose_event_id,n.notification_type,n.channel,n.subject,n.body,n.deep_link,n.attempts")
                .param("now",Timestamp.from(now)).param("stale",Timestamp.from(stale)).param("limit",batchSize).param("worker",workerId)
                .query((rs,row)->new ClaimedNotification(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getObject(3,UUID.class),
                        rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getInt(9))).list();
    }

    public String destination(ClaimedNotification notification){
        if(notification.channel().equals("EMAIL"))return jdbc.sql("select email from user_accounts where id=:user")
                .param("user",notification.userId()).query(String.class).single();
        return jdbc.sql("select chat_id from telegram_accounts where user_id=:user and linked_at is not null")
                .param("user",notification.userId()).query(String.class).optional().orElseThrow(()->new IllegalStateException("telegram-not-linked"));
    }

    public boolean isRelevant(ClaimedNotification notification){
        if(notification.doseEventId()==null)return true;
        String requiredStatus=switch(notification.type()){case "DOSE_DUE","DOSE_FOLLOWUP"->"PENDING";case "DOSE_MISSED"->"MISSED";default->null;};
        if(requiredStatus==null)return true;
        return jdbc.sql("select count(*) from dose_events where id=:id and status=:status").param("id",notification.doseEventId())
                .param("status",requiredStatus).query(Long.class).single()>0;
    }

    public void markSent(UUID id,Instant now){jdbc.sql("update notifications set status='SENT',sent_at=:now,claimed_at=null,claimed_by=null where id=:id")
            .param("now",Timestamp.from(now)).param("id",id).update();}

    public void markFailed(UUID id,int previousAttempts,int maximumAttempts,Instant now,String errorCode){
        int attempts=previousAttempts+1;String status=attempts>=maximumAttempts?"DEAD":"FAILED";
        long delaySeconds=Math.min(3600L,30L*(1L<<Math.min(attempts-1,10)));
        jdbc.sql("update notifications set status=:status,attempts=:attempts,next_attempt_at=:next,last_error_code=:error,claimed_at=null,claimed_by=null where id=:id")
                .param("status",status).param("attempts",attempts).param("next",Timestamp.from(now.plusSeconds(delaySeconds)))
                .param("error",errorCode).param("id",id).update();
    }

    public record PlannedNotification(UUID userId,UUID householdId,UUID doseEventId,UUID medicineId,String channel,
                                      String type,String priority,String subject,String body,String deepLink,String dedupeKey,
                                      Instant availableAt,Instant createdAt) { }
    public record ClaimedNotification(UUID id,UUID userId,UUID doseEventId,String type,String channel,String subject,String body,String deepLink,int attempts) { }
}
