package com.khayaal.household;

import com.khayaal.identity.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class HouseholdController {
    private final JdbcClient jdbc; private final HouseholdAuthorization authorization; private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    public HouseholdController(JdbcClient jdbc, HouseholdAuthorization authorization, Clock clock) {
        this.jdbc=jdbc; this.authorization=authorization; this.clock=clock;
    }

    @PostMapping("/households") @ResponseStatus(HttpStatus.CREATED) @Transactional
    HouseholdView create(@Valid @RequestBody CreateHousehold request, Authentication authentication) {
        ZoneId.of(request.defaultTimezone());
        UUID id=UUID.randomUUID(), user=CurrentUser.require(authentication).userId(); Instant now=clock.instant();
        jdbc.sql("insert into households(id,name,default_timezone,created_at) values (:id,:name,:zone,:now)")
                .param("id",id).param("name",request.name().trim()).param("zone",request.defaultTimezone()).param("now",Timestamp.from(now)).update();
        jdbc.sql("insert into household_memberships(id,household_id,user_id,role) values (:id,:household,:user,'OWNER')")
                .param("id",UUID.randomUUID()).param("household",id).param("user",user).update();
        return new HouseholdView(id,request.name().trim(),request.defaultTimezone(),HouseholdRole.OWNER);
    }

    @GetMapping("/households")
    List<HouseholdView> list(Authentication authentication) {
        return jdbc.sql("select h.id,h.name,h.default_timezone,m.role from households h join household_memberships m on m.household_id=h.id where m.user_id=:user order by h.name")
                .param("user",CurrentUser.require(authentication).userId()).query((rs,n)->new HouseholdView(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),HouseholdRole.valueOf(rs.getString(4)))).list();
    }

    @GetMapping("/households/{householdId}/members")
    List<MemberView> members(@PathVariable UUID householdId, Authentication authentication) {
        authorization.requireMembership(CurrentUser.require(authentication).userId(), householdId);
        return jdbc.sql("select u.id,u.display_name,u.email,m.role from household_memberships m join user_accounts u on u.id=m.user_id where m.household_id=:household order by u.display_name")
                .param("household", householdId).query((rs,row)->new MemberView(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),HouseholdRole.valueOf(rs.getString(4)))).list();
    }

    @PutMapping("/households/{householdId}/members/{userId}") @Transactional
    MemberView changeRole(@PathVariable UUID householdId,@PathVariable UUID userId,@Valid @RequestBody MemberRoleRequest request,Authentication authentication) {
        UUID actor=CurrentUser.require(authentication).userId();authorization.requireOwner(actor,householdId);
        if(actor.equals(userId))throw new ResponseStatusException(HttpStatus.CONFLICT,"Transfer ownership before changing your own role");
        if(request.role()==HouseholdRole.OWNER)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ownership transfer is not supported");
        int changed=jdbc.sql("update household_memberships set role=:role where household_id=:household and user_id=:user")
                .param("role",request.role().name()).param("household",householdId).param("user",userId).update();
        if(changed==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Member not found");
        return jdbc.sql("select u.id,u.display_name,u.email,m.role from household_memberships m join user_accounts u on u.id=m.user_id where m.household_id=:household and m.user_id=:user")
                .param("household",householdId).param("user",userId).query((rs,row)->new MemberView(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),HouseholdRole.valueOf(rs.getString(4)))).single();
    }

    @DeleteMapping("/households/{householdId}/members/{userId}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
    void removeMember(@PathVariable UUID householdId,@PathVariable UUID userId,Authentication authentication) {
        UUID actor=CurrentUser.require(authentication).userId();authorization.requireOwner(actor,householdId);
        if(actor.equals(userId))throw new ResponseStatusException(HttpStatus.CONFLICT,"The owner cannot remove themselves");
        jdbc.sql("delete from household_memberships where household_id=:household and user_id=:user and role<>'OWNER'")
                .param("household",householdId).param("user",userId).update();
    }

    @GetMapping("/households/{householdId}/invites")
    List<PendingInviteView> pendingInvites(@PathVariable UUID householdId,Authentication authentication){
        authorization.requireOwner(CurrentUser.require(authentication).userId(),householdId);
        return jdbc.sql("select id,role,expires_at,created_at from household_invites where household_id=:household and accepted_at is null and expires_at>:now order by created_at desc")
                .param("household",householdId).param("now",Timestamp.from(clock.instant())).query((rs,row)->new PendingInviteView(rs.getObject(1,UUID.class),HouseholdRole.valueOf(rs.getString(2)),rs.getObject(3,java.time.OffsetDateTime.class).toInstant(),rs.getObject(4,java.time.OffsetDateTime.class).toInstant())).list();
    }

    @PostMapping("/households/{householdId}/invites") @ResponseStatus(HttpStatus.CREATED) @Transactional
    InviteView invite(@PathVariable UUID householdId, @Valid @RequestBody InviteRequest request, Authentication authentication) {
        UUID user=CurrentUser.require(authentication).userId(); authorization.requireOwner(user,householdId);
        if(request.role()==HouseholdRole.OWNER) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invites cannot assign owner");
        byte[] bytes=new byte[24]; random.nextBytes(bytes); String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expires=clock.instant().plus(request.validHours(),ChronoUnit.HOURS);
        jdbc.sql("insert into household_invites(id,household_id,token_hash,role,expires_at,created_by,created_at) values (:id,:household,:hash,:role,:expires,:user,:now)")
                .param("id",UUID.randomUUID()).param("household",householdId).param("hash",hash(token)).param("role",request.role().name())
                .param("expires",Timestamp.from(expires)).param("user",user).param("now",Timestamp.from(clock.instant())).update();
        return new InviteView(token,expires,request.role());
    }

    @PostMapping("/invites/{token}/accept") @Transactional
    HouseholdView accept(@PathVariable String token, Authentication authentication) {
        UUID user=CurrentUser.require(authentication).userId(); Instant now=clock.instant();
        InviteRow invite=jdbc.sql("select id,household_id,role from household_invites where token_hash=:hash and accepted_at is null and expires_at>:now for update")
                .param("hash",hash(token)).param("now",Timestamp.from(now)).query((rs,n)->new InviteRow(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),HouseholdRole.valueOf(rs.getString(3))))
                .optional().orElseThrow(()->new ResponseStatusException(HttpStatus.GONE,"Invite is invalid, expired, or already used"));
        if (jdbc.sql("select count(*) from household_memberships where household_id=:household and user_id=:user")
                .param("household", invite.householdId()).param("user", user).query(Long.class).single() > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already a household member");
        }
        jdbc.sql("insert into household_memberships(id,household_id,user_id,role) values (:id,:household,:user,:role)")
                .param("id",UUID.randomUUID()).param("household",invite.householdId()).param("user",user).param("role",invite.role().name()).update();
        jdbc.sql("update household_invites set accepted_at=:now where id=:id and accepted_at is null").param("now",Timestamp.from(now)).param("id",invite.id()).update();
        return jdbc.sql("select id,name,default_timezone from households where id=:id").param("id",invite.householdId())
                .query((rs,n)->new HouseholdView(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),invite.role())).single();
    }

    private String hash(String value) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch(Exception e){throw new IllegalStateException(e);} }
    record CreateHousehold(@NotBlank @Size(max=100) String name,@NotBlank String defaultTimezone) { }
    record InviteRequest(@NotNull HouseholdRole role,long validHours) { public InviteRequest { if(validHours<1||validHours>168) throw new IllegalArgumentException("validHours must be between 1 and 168"); } }
    record HouseholdView(UUID id,String name,String defaultTimezone,HouseholdRole role) { }
    record InviteView(String token,Instant expiresAt,HouseholdRole role) { }
    record MemberRoleRequest(@NotNull HouseholdRole role) { }
    record MemberView(UUID userId,String displayName,String email,HouseholdRole role) { }
    record PendingInviteView(UUID id,HouseholdRole role,Instant expiresAt,Instant createdAt) { }
    private record InviteRow(UUID id,UUID householdId,HouseholdRole role) { }
}
