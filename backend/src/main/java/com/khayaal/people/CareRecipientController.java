package com.khayaal.people;

import com.khayaal.household.HouseholdAuthorization;
import com.khayaal.identity.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/households/{householdId}/people")
public class CareRecipientController {
    private final JdbcClient jdbc; private final HouseholdAuthorization authorization;
    public CareRecipientController(JdbcClient jdbc, HouseholdAuthorization authorization){this.jdbc=jdbc;this.authorization=authorization;}

    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional
    RecipientView create(@PathVariable UUID householdId,@Valid @RequestBody RecipientRequest request,Authentication auth){
        authorization.requireWriter(CurrentUser.require(auth).userId(),householdId); ZoneId.of(request.timezone()); validateLinkedUser(householdId,request.linkedUserId()); UUID id=UUID.randomUUID();
        jdbc.sql("insert into care_recipients(id,household_id,name,birth_year,timezone,notes,linked_user_id,archived) values (:id,:household,:name,:year,:zone,:notes,:linked,false)")
                .param("id",id).param("household",householdId).param("name",request.name().trim()).param("year",request.birthYear())
                .param("zone",request.timezone()).param("notes",request.notes()).param("linked",request.linkedUserId()).update(); return view(id);
    }

    @GetMapping
    List<RecipientView> list(@PathVariable UUID householdId,Authentication auth){ authorization.requireMembership(CurrentUser.require(auth).userId(),householdId);
        return jdbc.sql("select id,name,birth_year,timezone,notes,linked_user_id,archived from care_recipients where household_id=:household order by name")
                .param("household",householdId).query((rs,n)->map(rs)).list(); }

    @PutMapping("/{recipientId}") @Transactional
    RecipientView update(@PathVariable UUID householdId,@PathVariable UUID recipientId,@Valid @RequestBody RecipientRequest request,Authentication auth){
        authorization.requireWriter(CurrentUser.require(auth).userId(),householdId); ZoneId.of(request.timezone()); validateLinkedUser(householdId,request.linkedUserId());
        int changed=jdbc.sql("update care_recipients set name=:name,birth_year=:year,timezone=:zone,notes=:notes,linked_user_id=:linked where id=:id and household_id=:household")
                .param("name",request.name().trim()).param("year",request.birthYear()).param("zone",request.timezone()).param("notes",request.notes())
                .param("linked",request.linkedUserId()).param("id",recipientId).param("household",householdId).update(); if(changed==0) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND); return view(recipientId); }

    @DeleteMapping("/{recipientId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void archive(@PathVariable UUID householdId,@PathVariable UUID recipientId,Authentication auth){ authorization.requireWriter(CurrentUser.require(auth).userId(),householdId);
        jdbc.sql("update care_recipients set archived=true where id=:id and household_id=:household").param("id",recipientId).param("household",householdId).update(); }

    private void validateLinkedUser(UUID householdId,UUID linkedUserId){if(linkedUserId!=null&&jdbc.sql("select count(*) from household_memberships where household_id=:household and user_id=:user").param("household",householdId).param("user",linkedUserId).query(Long.class).single()==0)throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"Linked user must belong to the household");}
    private RecipientView view(UUID id){return jdbc.sql("select id,name,birth_year,timezone,notes,linked_user_id,archived from care_recipients where id=:id").param("id",id).query((rs,n)->map(rs)).single();}
    private RecipientView map(java.sql.ResultSet rs)throws java.sql.SQLException{return new RecipientView(rs.getObject(1,UUID.class),rs.getString(2),(Integer)rs.getObject(3),rs.getString(4),rs.getString(5),rs.getObject(6,UUID.class),rs.getBoolean(7));}
    record RecipientRequest(@NotBlank @Size(max=100) String name,@jakarta.validation.constraints.Min(1900) Integer birthYear,@NotBlank String timezone,@Size(max=1000) String notes,UUID linkedUserId) { }
    record RecipientView(UUID id,String name,Integer birthYear,String timezone,String notes,UUID linkedUserId,boolean archived) { }
}
