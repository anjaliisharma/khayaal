package com.khayaal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khayaal.doses.DoseEventGenerator;
import com.khayaal.notify.NotificationPlanner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class Phase2IntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcClient jdbc;
    @Autowired DoseEventGenerator generator;
    @Autowired NotificationPlanner notificationPlanner;

    @Test
    void refreshTokensRotateAndCannotBeReplayed() throws Exception {
        MvcResult registered = mvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"rotate@example.test\",\"password\":\"correct-horse-battery\",\"displayName\":\"Rotate User\"}"))
                .andExpect(status().isOk()).andReturn();
        jakarta.servlet.http.Cookie original = registered.getResponse().getCookie("khayaal_refresh");
        assertThat(original).isNotNull();
        mvc.perform(post("/api/auth/refresh").cookie(original)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/refresh").cookie(original)).andExpect(status().isUnauthorized());
    }

    @Test
    void rolesIsolationVersioningAndDoseGenerationAreEnforced() throws Exception {
        String ownerToken = register("owner@example.test");
        String viewerToken = register("viewer@example.test");
        UUID householdId = id(authenticatedPost("/api/households", ownerToken,
                "{\"name\":\"Home\",\"defaultTimezone\":\"Asia/Kolkata\"}"));
        UUID recipientId = id(authenticatedPost("/api/households/" + householdId + "/people", ownerToken,
                "{\"name\":\"Parent\",\"birthYear\":1960,\"timezone\":\"Asia/Kolkata\",\"notes\":null}"));

        LocalDate startDate = LocalDate.now().plusDays(1);
        String medicineJson = "{\"name\":\"Synthetic medicine\",\"strength\":\"user-entered\",\"form\":\"tablet\"," +
                "\"unitsPerDose\":1.00,\"instructions\":\"user-entered\",\"startDate\":\"" + startDate + "\"," +
                "\"endDate\":null,\"asNeeded\":false,\"leadTimeDays\":3,\"schedule\":{\"type\":\"DAILY\"," +
                "\"localTimes\":[\"08:00:00\"],\"daysOfWeek\":[],\"intervalDays\":null,\"anchorDate\":null}}";
        UUID medicineId = id(authenticatedPost("/api/people/" + recipientId + "/medicines", ownerToken, medicineJson));
        long initialCount = count("select count(*) from dose_events where medicine_id=:id", medicineId);
        assertThat(initialCount).isPositive();

        generator.extendRollingHorizon();
        assertThat(count("select count(*) from dose_events where medicine_id=:id", medicineId)).isEqualTo(initialCount);

        LocalDateTime firstLocal = jdbc.sql("select scheduled_local from dose_events where medicine_id=:id order by scheduled_at limit 1")
                .param("id", medicineId).query(LocalDateTime.class).single();
        assertThat(firstLocal.toLocalTime()).isEqualTo(LocalTime.of(8, 0));

        String inviteBody = authenticatedPost("/api/households/" + householdId + "/invites", ownerToken,
                "{\"role\":\"VIEWER\",\"validHours\":24}");
        String inviteToken = json.readTree(inviteBody).get("token").asText();
        mvc.perform(post("/api/invites/" + inviteToken + "/accept").header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk());
        mvc.perform(post("/api/invites/" + inviteToken + "/accept").header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isGone());
        mvc.perform(post("/api/households/" + householdId + "/people").header("Authorization", "Bearer " + viewerToken)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"Denied\",\"timezone\":\"UTC\"}"))
                .andExpect(status().isForbidden());

        String outsiderToken = register("outsider@example.test");
        mvc.perform(get("/api/households/" + householdId + "/people").header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isNotFound());

        Instant effectiveFrom = Instant.now().plusSeconds(60);
        jdbc.sql("insert into dose_events(id,medicine_id,scheduled_at,scheduled_local,status) values (:id,:medicine,:at,:local,'PENDING')")
                .param("id",UUID.randomUUID()).param("medicine",medicineId).param("at",Timestamp.from(effectiveFrom.minusSeconds(3600)))
                .param("local",LocalDateTime.now().minusHours(1)).update();
        mvc.perform(put("/api/medicines/" + medicineId + "/schedule").header("Authorization","Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON).content("{\"effectiveFrom\":\"" + effectiveFrom + "\",\"schedule\":{\"type\":\"DAILY\",\"localTimes\":[\"09:00:00\"],\"daysOfWeek\":[],\"intervalDays\":null,\"anchorDate\":null}}"))
                .andExpect(status().isOk());
        assertThat(count("select count(*) from dose_events where medicine_id=:id and scheduled_at<:cutoff", medicineId, effectiveFrom)).isPositive();
        assertThat(count("select count(*) from schedule_rule_versions where medicine_id=:id", medicineId)).isEqualTo(2);
    }

    @Test
    void purchasesAddAndDoseReplayAndUndoChangeStockExactlyOnce() throws Exception {
        String ownerToken = register("stock-owner@example.test");
        UUID householdId = id(authenticatedPost("/api/households", ownerToken,
                "{\"name\":\"Stock Home\",\"defaultTimezone\":\"Asia/Kolkata\"}"));
        UUID recipientId = id(authenticatedPost("/api/households/" + householdId + "/people", ownerToken,
                "{\"name\":\"Stock Recipient\",\"timezone\":\"Asia/Kolkata\"}"));
        LocalDate startDate = LocalDate.now().plusDays(1);
        UUID medicineId = id(authenticatedPost("/api/people/" + recipientId + "/medicines", ownerToken,
                "{\"name\":\"Synthetic stock medicine\",\"form\":\"tablet\",\"unitsPerDose\":1.00," +
                        "\"startDate\":\"" + startDate + "\",\"asNeeded\":false,\"leadTimeDays\":3," +
                        "\"schedule\":{\"type\":\"DAILY\",\"localTimes\":[\"08:00:00\"]}}"));

        authenticatedPost("/api/medicines/" + medicineId + "/purchases", ownerToken,
                "{\"packCount\":1,\"unitsPerPack\":4.00,\"purchasedOn\":\"" + LocalDate.now() + "\"}");
        authenticatedPost("/api/medicines/" + medicineId + "/purchases", ownerToken,
                "{\"packCount\":1,\"unitsPerPack\":10.00,\"purchasedOn\":\"" + LocalDate.now() + "\"}");
        assertThat(stockBalance(medicineId)).isEqualByComparingTo("14.00");

        UUID doseId = jdbc.sql("select id from dose_events where medicine_id=:medicine order by scheduled_at limit 1")
                .param("medicine", medicineId).query(UUID.class).single();
        mvc.perform(post("/api/doses/" + doseId + "/taken").header("Authorization", "Bearer " + ownerToken)
                        .header("Idempotency-Key", "take-once").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/doses/" + doseId + "/taken").header("Authorization", "Bearer " + ownerToken)
                        .header("Idempotency-Key", "take-once").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        assertThat(stockBalance(medicineId)).isEqualByComparingTo("13.00");
        assertThat(count("select count(*) from stock_ledger_entries where dose_event_id=:id and reason='DOSE_TAKEN'", doseId)).isEqualTo(1);

        mvc.perform(post("/api/doses/" + doseId + "/undo").header("Authorization", "Bearer " + ownerToken)
                        .header("Idempotency-Key", "undo-once"))
                .andExpect(status().isOk());
        assertThat(stockBalance(medicineId)).isEqualByComparingTo("14.00");
    }

    @Test
    void concurrentCaregiversCreateOneTakenDeduction() throws Exception {
        String ownerToken = register("concurrent-owner@example.test");
        String caregiverToken = register("concurrent-caregiver@example.test");
        UUID householdId = id(authenticatedPost("/api/households", ownerToken,
                "{\"name\":\"Concurrent Home\",\"defaultTimezone\":\"UTC\"}"));
        UUID caregiverId = jdbc.sql("select id from user_accounts where email='concurrent-caregiver@example.test'").query(UUID.class).single();
        jdbc.sql("insert into household_memberships(id,household_id,user_id,role) values (:id,:household,:user,'CAREGIVER')")
                .param("id", UUID.randomUUID()).param("household", householdId).param("user", caregiverId).update();
        UUID recipientId = id(authenticatedPost("/api/households/" + householdId + "/people", ownerToken,
                "{\"name\":\"Concurrent Recipient\",\"timezone\":\"UTC\"}"));
        LocalDate startDate = LocalDate.now().plusDays(1);
        UUID medicineId = id(authenticatedPost("/api/people/" + recipientId + "/medicines", ownerToken,
                "{\"name\":\"Synthetic concurrent medicine\",\"form\":\"tablet\",\"unitsPerDose\":1.00," +
                        "\"startDate\":\"" + startDate + "\",\"asNeeded\":false,\"leadTimeDays\":3," +
                        "\"schedule\":{\"type\":\"DAILY\",\"localTimes\":[\"08:00:00\"]}}"));
        authenticatedPost("/api/medicines/" + medicineId + "/purchases", ownerToken,
                "{\"packCount\":1,\"unitsPerPack\":10.00,\"purchasedOn\":\"" + LocalDate.now() + "\"}");
        UUID doseId = jdbc.sql("select id from dose_events where medicine_id=:medicine order by scheduled_at limit 1")
                .param("medicine", medicineId).query(UUID.class).single();

        CountDownLatch ready = new CountDownLatch(2); CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var owner = executor.submit(() -> takeConcurrently(doseId, ownerToken, "owner-confirmation", ready, start));
            var caregiver = executor.submit(() -> takeConcurrently(doseId, caregiverToken, "caregiver-confirmation", ready, start));
            ready.await(); start.countDown();
            assertThat(owner.get()).isEqualTo(200); assertThat(caregiver.get()).isEqualTo(200);
        }
        assertThat(count("select count(*) from stock_ledger_entries where dose_event_id=:id and reason='DOSE_TAKEN'", doseId)).isEqualTo(1);
        assertThat(stockBalance(medicineId)).isEqualByComparingTo("9.00");
    }

    @Test
    void notificationPlanningIsDeduplicatedDiscreetAndHonorsDoseBoundaries() throws Exception {
        String token = register("notification-owner@example.test");
        UUID userId = jdbc.sql("select id from user_accounts where email='notification-owner@example.test'").query(UUID.class).single();
        UUID householdId = id(authenticatedPost("/api/households", token,
                "{\"name\":\"Notification Home\",\"defaultTimezone\":\"UTC\"}"));
        UUID recipientId = id(authenticatedPost("/api/households/" + householdId + "/people", token,
                "{\"name\":\"Reminder Recipient\",\"timezone\":\"UTC\"}"));
        LocalDate startDate = LocalDate.now().plusDays(1);
        UUID medicineId = id(authenticatedPost("/api/people/" + recipientId + "/medicines", token,
                "{\"name\":\"Private synthetic medicine\",\"form\":\"tablet\",\"unitsPerDose\":1.00," +
                        "\"startDate\":\"" + startDate + "\",\"asNeeded\":false,\"leadTimeDays\":3," +
                        "\"schedule\":{\"type\":\"DAILY\",\"localTimes\":[\"08:00:00\"]}}"));
        jdbc.sql("insert into notification_preferences(user_id,email_enabled,telegram_enabled,discreet_mode,dose_missed_enabled,timezone) values (:user,true,false,true,true,'UTC')")
                .param("user", userId).update();
        DoseAt dose = jdbc.sql("select id,scheduled_at from dose_events where medicine_id=:medicine order by scheduled_at limit 1")
                .param("medicine", medicineId).query((rs,row)->new DoseAt(rs.getObject(1,UUID.class),rs.getObject(2,java.time.OffsetDateTime.class).toInstant())).single();

        notificationPlanner.planAt(dose.scheduledAt().plusSeconds(1));
        notificationPlanner.planAt(dose.scheduledAt().plusSeconds(1));
        assertThat(notificationCount(dose.id(), "DOSE_DUE")).isEqualTo(1);
        String dueBody=jdbc.sql("select body from notifications where dose_event_id=:dose and notification_type='DOSE_DUE'")
                .param("dose",dose.id()).query(String.class).single();
        assertThat(dueBody).doesNotContain("Private synthetic medicine").contains("Reminder Recipient");

        notificationPlanner.planAt(dose.scheduledAt().plusSeconds(16*60L));
        assertThat(notificationCount(dose.id(), "DOSE_FOLLOWUP")).isEqualTo(1);
        notificationPlanner.planAt(dose.scheduledAt().plusSeconds(121*60L));
        assertThat(notificationCount(dose.id(), "DOSE_MISSED")).isEqualTo(1);
        assertThat(jdbc.sql("select status from dose_events where id=:id").param("id",dose.id()).query(String.class).single()).isEqualTo("MISSED");
    }

    @Test
    void packUnitsAreWholeAndInsufficientDoseStockRecordsAShortageWithoutGoingNegative() throws Exception {
        String token = register("stock-invariants@example.test");
        UUID householdId = id(authenticatedPost("/api/households", token,
                "{\"name\":\"Invariant Home\",\"defaultTimezone\":\"UTC\"}"));
        UUID recipientId = id(authenticatedPost("/api/households/" + householdId + "/people", token,
                "{\"name\":\"Invariant Recipient\",\"timezone\":\"UTC\"}"));
        UUID medicineId = id(authenticatedPost("/api/people/" + recipientId + "/medicines", token,
                "{\"name\":\"Invariant medicine\",\"form\":\"tablet\",\"unitsPerDose\":1.00," +
                        "\"startDate\":\"" + LocalDate.now().plusDays(1) + "\",\"asNeeded\":false,\"leadTimeDays\":3," +
                        "\"schedule\":{\"type\":\"DAILY\",\"localTimes\":[\"08:00:00\"]}}"));

        mvc.perform(post("/api/medicines/" + medicineId + "/purchases")
                        .header("Authorization", "Bearer " + token).contentType(APPLICATION_JSON)
                        .content("{\"packCount\":1,\"unitsPerPack\":27.25,\"purchasedOn\":\"" + LocalDate.now() + "\"}"))
                .andExpect(status().isBadRequest());

        UUID doseId = jdbc.sql("select id from dose_events where medicine_id=:medicine order by scheduled_at limit 1")
                .param("medicine", medicineId).query(UUID.class).single();
        mvc.perform(post("/api/doses/" + doseId + "/taken").header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "shortage-dose").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        assertThat(stockBalance(medicineId)).isZero();
        assertThat(jdbc.sql("select unmet_units from stock_shortages where dose_event_id=:dose")
                .param("dose", doseId).query(java.math.BigDecimal.class).single()).isEqualByComparingTo("1");

        mvc.perform(post("/api/medicines/" + medicineId + "/stock/discard")
                        .header("Authorization", "Bearer " + token).contentType(APPLICATION_JSON)
                        .content("{\"units\":1,\"note\":\"cannot discard absent stock\"}"))
                .andExpect(status().isConflict());
        assertThat(stockBalance(medicineId)).isZero();
    }

    @Test
    void confirmedAccountDeletionPurgesSoleMemberHouseholdAndAnonymizesAuditIdentity() throws Exception {
        String token = register("delete-me@example.test");
        UUID userId = jdbc.sql("select id from user_accounts where email='delete-me@example.test'").query(UUID.class).single();
        UUID householdId = id(authenticatedPost("/api/households", token,
                "{\"name\":\"Delete Home\",\"defaultTimezone\":\"UTC\"}"));
        authenticatedPost("/api/households/" + householdId + "/people", token,
                "{\"name\":\"Delete Recipient\",\"timezone\":\"UTC\"}");

        mvc.perform(delete("/api/auth/account").param("confirmation", "DELETE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(jdbc.sql("select count(*) from households where id=:id").param("id", householdId).query(Long.class).single()).isZero();
        assertThat(jdbc.sql("select email from user_accounts where id=:id").param("id", userId).query(String.class).single())
                .startsWith("deleted+").endsWith("@invalid.local");
        assertThat(jdbc.sql("select count(*) from household_memberships where user_id=:id").param("id", userId).query(Long.class).single()).isZero();
    }

    private String register(String email) throws Exception {
        String body=mvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON)
                        .content("{\"email\":\""+email+"\",\"password\":\"correct-horse-battery\",\"displayName\":\"Test User\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }
    private String authenticatedPost(String path,String token,String body)throws Exception{return mvc.perform(post(path).header("Authorization","Bearer "+token).contentType(APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();}
    private UUID id(String body)throws Exception{return UUID.fromString(json.readTree(body).get("id").asText());}
    private long count(String sql,UUID id){return jdbc.sql(sql).param("id",id).query(Long.class).single();}
    private long count(String sql,UUID id,Instant cutoff){return jdbc.sql(sql).param("id",id).param("cutoff",Timestamp.from(cutoff)).query(Long.class).single();}
    private java.math.BigDecimal stockBalance(UUID medicineId){return jdbc.sql("select coalesce(sum(delta),0) from stock_ledger_entries where medicine_id=:id").param("id",medicineId).query(java.math.BigDecimal.class).single();}
    private int takeConcurrently(UUID doseId,String token,String key,CountDownLatch ready,CountDownLatch start)throws Exception{
        ready.countDown();start.await();return mvc.perform(post("/api/doses/"+doseId+"/taken").header("Authorization","Bearer "+token)
                .header("Idempotency-Key",key).contentType(APPLICATION_JSON).content("{}")).andReturn().getResponse().getStatus();
    }
    private long notificationCount(UUID doseId,String type){return jdbc.sql("select count(*) from notifications where dose_event_id=:dose and notification_type=:type")
            .param("dose",doseId).param("type",type).query(Long.class).single();}
    private record DoseAt(UUID id,Instant scheduledAt) { }
}
