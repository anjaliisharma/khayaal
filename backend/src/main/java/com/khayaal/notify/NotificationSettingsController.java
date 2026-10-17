package com.khayaal.notify;

import com.khayaal.identity.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.*;
import java.util.HexFormat;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class NotificationSettingsController {
    private final JdbcClient jdbc;private final NotificationOutbox outbox;private final NotificationProperties properties;private final Clock clock;
    private final SecureRandom random=new SecureRandom();
    public NotificationSettingsController(JdbcClient jdbc,NotificationOutbox outbox,NotificationProperties properties,Clock clock){this.jdbc=jdbc;this.outbox=outbox;this.properties=properties;this.clock=clock;}

    @GetMapping("/notification-preferences")
    Preferences preferences(Authentication authentication){
        UUID user=CurrentUser.require(authentication).userId();
        return jdbc.sql("select email_enabled,telegram_enabled,discreet_mode,dose_missed_enabled,quiet_hours_start,quiet_hours_end,timezone from notification_preferences where user_id=:user")
                .param("user",user).query((rs,row)->new Preferences(rs.getBoolean(1),rs.getBoolean(2),rs.getBoolean(3),rs.getBoolean(4),
                        rs.getObject(5,LocalTime.class),rs.getObject(6,LocalTime.class),rs.getString(7))).optional()
                .orElse(new Preferences(true,false,false,true,null,null,"UTC"));
    }

    @PutMapping("/notification-preferences") @Transactional
    Preferences preferences(@Valid @RequestBody Preferences request,Authentication authentication){
        ZoneId.of(request.timezone());UUID user=CurrentUser.require(authentication).userId();
        jdbc.sql("insert into notification_preferences(user_id,email_enabled,telegram_enabled,discreet_mode,dose_missed_enabled,quiet_hours_start,quiet_hours_end,timezone) " +
                        "values (:user,:email,:telegram,:discreet,:missed,:quietStart,:quietEnd,:zone) on conflict(user_id) do update set " +
                        "email_enabled=excluded.email_enabled,telegram_enabled=excluded.telegram_enabled,discreet_mode=excluded.discreet_mode," +
                        "dose_missed_enabled=excluded.dose_missed_enabled,quiet_hours_start=excluded.quiet_hours_start,quiet_hours_end=excluded.quiet_hours_end,timezone=excluded.timezone")
                .param("user",user).param("email",request.emailEnabled()).param("telegram",request.telegramEnabled()).param("discreet",request.discreetMode())
                .param("missed",request.doseMissedEnabled()).param("quietStart",request.quietHoursStart()).param("quietEnd",request.quietHoursEnd()).param("zone",request.timezone()).update();
        return request;
    }

    @PostMapping("/telegram/link-code") @Transactional
    LinkCode linkCode(Authentication authentication){
        UUID user=CurrentUser.require(authentication).userId();String code="%06d".formatted(random.nextInt(1_000_000));Instant expires=clock.instant().plusSeconds(600);
        jdbc.sql("insert into telegram_accounts(user_id,link_code_hash,link_code_expires_at) values (:user,:hash,:expires) on conflict(user_id) do update set " +
                        "link_code_hash=excluded.link_code_hash,link_code_expires_at=excluded.link_code_expires_at")
                .param("user",user).param("hash",hash(code)).param("expires",Timestamp.from(expires)).update();
        return new LinkCode(code,expires);
    }

    @PostMapping("/telegram/complete-link") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
    void completeLink(@Valid @RequestBody CompleteLink request){
        UUID user=jdbc.sql("select user_id from telegram_accounts where link_code_hash=:hash and link_code_expires_at>:now for update")
                .param("hash",hash(request.code())).param("now",Timestamp.from(clock.instant())).query(UUID.class).optional()
                .orElseThrow(()->new ResponseStatusException(HttpStatus.GONE,"Link code is invalid or expired"));
        jdbc.sql("update telegram_accounts set chat_id=:chat,linked_at=:now,link_code_hash=null,link_code_expires_at=null where user_id=:user")
                .param("chat",request.chatId()).param("now",Timestamp.from(clock.instant())).param("user",user).update();
    }

    @PostMapping("/notifications/test") @ResponseStatus(HttpStatus.ACCEPTED)
    void test(@RequestParam(defaultValue="EMAIL") String channel,Authentication authentication){
        UUID user=CurrentUser.require(authentication).userId();UUID household=jdbc.sql("select household_id from household_memberships where user_id=:user order by household_id limit 1")
                .param("user",user).query(UUID.class).optional().orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"Join a household before testing notifications"));
        Instant now=clock.instant();outbox.enqueue(new NotificationOutbox.PlannedNotification(user,household,null,null,channel,"TEST","NORMAL","Khayaal test notification",
                "Your Khayaal notifications are configured.",properties.applicationUrl()+"/settings","TEST:"+user+":"+channel+":"+now.toEpochMilli(),now,now));
    }

    private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    record Preferences(boolean emailEnabled,boolean telegramEnabled,boolean discreetMode,boolean doseMissedEnabled,LocalTime quietHoursStart,LocalTime quietHoursEnd,@NotBlank String timezone){ }
    record LinkCode(String code,Instant expiresAt){ }
    record CompleteLink(@NotBlank String code,@NotBlank String chatId){ }
}
