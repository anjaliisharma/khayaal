package com.khayaal.identity;

import java.util.UUID;

public record AuthenticatedUser(UUID userId, String email) { }
