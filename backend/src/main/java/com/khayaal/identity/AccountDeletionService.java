package com.khayaal.identity;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class AccountDeletionService {
    private final JdbcClient jdbc;

    public AccountDeletionService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void delete(UUID userId) {
        List<UUID> ownedHouseholds = jdbc.sql("select household_id from household_memberships where user_id=:user and role='OWNER'")
                .param("user", userId).query(UUID.class).list();
        for (UUID householdId : ownedHouseholds) {
            long otherMembers = jdbc.sql("select count(*) from household_memberships where household_id=:household and user_id<>:user")
                    .param("household", householdId).param("user", userId).query(Long.class).single();
            if (otherMembers > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Remove the other household members before deleting the owner account");
            }
            deleteHousehold(householdId);
        }
        jdbc.sql("update care_recipients set linked_user_id=null where linked_user_id=:user").param("user", userId).update();
        jdbc.sql("delete from household_memberships where user_id=:user").param("user", userId).update();
        // Keep a non-identifying tombstone because immutable audit and ledger rows retain actor IDs.
        // Memberships and refresh tokens are removed, so the tombstone cannot authenticate or access data.
        jdbc.sql("delete from refresh_tokens where user_id=:user").param("user", userId).update();
        jdbc.sql("delete from notification_preferences where user_id=:user").param("user", userId).update();
        jdbc.sql("delete from telegram_accounts where user_id=:user").param("user", userId).update();
        jdbc.sql("update user_accounts set email=:email,password_hash=:password,display_name='Deleted account' where id=:user")
                .param("email", "deleted+" + userId + "@invalid.local")
                .param("password", "ACCOUNT_DELETED_" + UUID.randomUUID())
                .param("user", userId).update();
    }

    private void deleteHousehold(UUID householdId) {
        jdbc.sql("delete from notifications where household_id=:household").param("household", householdId).update();
        jdbc.sql("delete from audit_events where household_id=:household").param("household", householdId).update();
        jdbc.sql("delete from dose_action_requests where dose_event_id in (select d.id from dose_events d join medicines m on m.id=d.medicine_id join care_recipients r on r.id=m.recipient_id where r.household_id=:household)")
                .param("household", householdId).update();
        jdbc.sql("delete from stock_shortages where medicine_id in (select m.id from medicines m join care_recipients r on r.id=m.recipient_id where r.household_id=:household)")
                .param("household", householdId).update();
        jdbc.sql("delete from stock_ledger_entries where medicine_id in (select m.id from medicines m join care_recipients r on r.id=m.recipient_id where r.household_id=:household)")
                .param("household", householdId).update();
        jdbc.sql("delete from stock_lots where medicine_id in (select m.id from medicines m join care_recipients r on r.id=m.recipient_id where r.household_id=:household)")
                .param("household", householdId).update();
        jdbc.sql("delete from purchases where medicine_id in (select m.id from medicines m join care_recipients r on r.id=m.recipient_id where r.household_id=:household)")
                .param("household", householdId).update();
        jdbc.sql("delete from reorder_requests where medicine_id in (select m.id from medicines m join care_recipients r on r.id=m.recipient_id where r.household_id=:household)")
                .param("household", householdId).update();
        jdbc.sql("delete from dose_events where medicine_id in (select m.id from medicines m join care_recipients r on r.id=m.recipient_id where r.household_id=:household)")
                .param("household", householdId).update();
        jdbc.sql("delete from schedule_rule_times where schedule_rule_id in (select s.id from schedule_rule_versions s join medicines m on m.id=s.medicine_id join care_recipients r on r.id=m.recipient_id where r.household_id=:household)")
                .param("household", householdId).update();
        jdbc.sql("delete from schedule_rule_days where schedule_rule_id in (select s.id from schedule_rule_versions s join medicines m on m.id=s.medicine_id join care_recipients r on r.id=m.recipient_id where r.household_id=:household)")
                .param("household", householdId).update();
        jdbc.sql("delete from schedule_rule_versions where medicine_id in (select m.id from medicines m join care_recipients r on r.id=m.recipient_id where r.household_id=:household)")
                .param("household", householdId).update();
        jdbc.sql("delete from medicines where recipient_id in (select id from care_recipients where household_id=:household)")
                .param("household", householdId).update();
        jdbc.sql("delete from care_recipients where household_id=:household").param("household", householdId).update();
        jdbc.sql("delete from household_invites where household_id=:household").param("household", householdId).update();
        jdbc.sql("delete from household_memberships where household_id=:household").param("household", householdId).update();
        jdbc.sql("delete from households where id=:household").param("household", householdId).update();
    }
}
