package com.khayaal.household;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
public class HouseholdAuthorization {
    private final JdbcClient jdbc;
    public HouseholdAuthorization(JdbcClient jdbc) { this.jdbc = jdbc; }

    public HouseholdRole requireMembership(UUID userId, UUID householdId) {
        return jdbc.sql("select role from household_memberships where user_id=:user and household_id=:household")
                .param("user", userId).param("household", householdId).query(String.class).optional()
                .map(HouseholdRole::valueOf).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Household not found"));
    }

    public void requireWriter(UUID userId, UUID householdId) {
        if (requireMembership(userId, householdId) == HouseholdRole.VIEWER)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Viewer role is read-only");
    }

    public void requireOwner(UUID userId, UUID householdId) {
        if (requireMembership(userId, householdId) != HouseholdRole.OWNER)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Owner role is required");
    }
}
