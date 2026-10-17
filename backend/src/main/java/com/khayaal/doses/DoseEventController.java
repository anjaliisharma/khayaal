package com.khayaal.doses;

import com.khayaal.household.HouseholdAuthorization;
import com.khayaal.identity.CurrentUser;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class DoseEventController {
    private final JdbcClient jdbc; private final HouseholdAuthorization authorization; private final DoseActionService actions;
    public DoseEventController(JdbcClient jdbc,HouseholdAuthorization authorization,DoseActionService actions){this.jdbc=jdbc;this.authorization=authorization;this.actions=actions;}
    @GetMapping("/households/{householdId}/doses")
    List<DoseView> list(@PathVariable UUID householdId,@RequestParam Instant from,@RequestParam Instant to,Authentication auth){
        authorization.requireMembership(CurrentUser.require(auth).userId(),householdId);
        return jdbc.sql("select d.id,d.medicine_id,d.scheduled_at,d.scheduled_local,d.status from dose_events d join medicines m on m.id=d.medicine_id join care_recipients r on r.id=m.recipient_id where r.household_id=:household and d.scheduled_at>=:from and d.scheduled_at<:to order by d.scheduled_at")
                .param("household",householdId).param("from",Timestamp.from(from)).param("to",Timestamp.from(to)).query((rs,n)->new DoseView(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getObject(3,java.time.OffsetDateTime.class).toInstant(),rs.getObject(4,LocalDateTime.class),rs.getString(5))).list();
    }
    @PostMapping("/doses/{doseId}/taken")
    DoseActionService.DoseActionResult taken(@PathVariable UUID doseId,@RequestHeader("Idempotency-Key") String key,
                                              @RequestHeader(value="X-Client-Identifier",required=false) String client,
                                              @RequestBody(required=false) DoseActionRequest request,Authentication auth){
        return actions.act(doseId,CurrentUser.require(auth).userId(),"TAKEN",key,request==null?null:request.actualTime(),request==null?null:request.note(),client);
    }
    @PostMapping("/doses/{doseId}/skip")
    DoseActionService.DoseActionResult skip(@PathVariable UUID doseId,@RequestHeader("Idempotency-Key") String key,
                                             @RequestHeader(value="X-Client-Identifier",required=false) String client,
                                             @RequestBody(required=false) DoseActionRequest request,Authentication auth){
        return actions.act(doseId,CurrentUser.require(auth).userId(),"SKIPPED",key,null,request==null?null:request.note(),client);
    }
    @PostMapping("/doses/{doseId}/undo")
    DoseActionService.DoseActionResult undo(@PathVariable UUID doseId,@RequestHeader("Idempotency-Key") String key,
                                             @RequestHeader(value="X-Client-Identifier",required=false) String client,Authentication auth){
        return actions.act(doseId,CurrentUser.require(auth).userId(),"UNDO",key,null,null,client);
    }
    @PostMapping("/medicines/{medicineId}/doses/prn") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    DoseActionService.DoseActionResult prn(@PathVariable UUID medicineId,@RequestHeader("Idempotency-Key") String key,
                                            @RequestHeader(value="X-Client-Identifier",required=false) String client,
                                            @RequestBody(required=false) DoseActionRequest request,Authentication auth){
        return actions.logPrn(medicineId,CurrentUser.require(auth).userId(),key,request==null?null:request.actualTime(),request==null?null:request.note(),client);
    }
    record DoseActionRequest(Instant actualTime,String note) { }
    record DoseView(UUID id,UUID medicineId,Instant scheduledAt,LocalDateTime scheduledLocal,String status) { }
}
