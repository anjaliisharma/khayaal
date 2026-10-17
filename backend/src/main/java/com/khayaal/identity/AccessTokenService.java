package com.khayaal.identity;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class AccessTokenService {
    private final JwtEncoder encoder;
    private final Clock clock;
    private final Duration lifetime;

    public AccessTokenService(JwtEncoder encoder, Clock clock, SecurityProperties properties) {
        this.encoder = encoder;
        this.clock = clock;
        this.lifetime = Duration.ofMinutes(properties.accessTokenMinutes());
    }

    public String issue(UUID userId, String email) {
        Instant issuedAt = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("khayaal").subject(userId.toString())
                .issuedAt(issuedAt).expiresAt(issuedAt.plus(lifetime)).claim("email", email).build();
        return encoder.encode(JwtEncoderParameters.from(
                org.springframework.security.oauth2.jwt.JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
