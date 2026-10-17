# Security review

- Access tokens are short-lived JWTs kept in browser memory. Refresh tokens are opaque, hashed at rest, rotated under a row lock, and stored in an httpOnly, SameSite=Strict cookie scoped to `/api/auth`. Set `APP_SECURE_COOKIE=true` behind production HTTPS.
- CSRF protection is disabled because authenticated application writes use an `Authorization` bearer token that another origin cannot attach. The refresh and logout endpoints accept only POST; their cookie is SameSite=Strict. Production must enable HTTPS and set the refresh cookie `Secure`.
- CORS allows one configured origin with credentials. Do not use a wildcard origin.
- Household authorization is centralized and the server remains the security boundary; hidden frontend controls are convenience only.
- Invite and refresh tokens are stored only as SHA-256 hashes. Notification errors store exception classes rather than message content. Application logs must never contain medicine names or notification bodies.
- Sensitive authentication and invite writes are rate-limited in memory. This protects local/single-instance use; production should enforce a shared limit at the edge or with Redis.
- Account deletion requires the exact confirmation `DELETE`. Sole-member owned households are transactionally purged; membership and channel data are removed and immutable actor references retain only a non-identifying account tombstone. Owners with other household members must remove those members first.

## Release checklist

1. Replace all development secrets and require HTTPS.
2. Set the refresh cookie to `Secure` in the production profile.
3. Restrict Swagger and actuator metrics at the network edge.
4. Run the role matrix, account deletion, invite, and log-scrubbing tests.
5. Review dependency and container scans and document accepted findings.
