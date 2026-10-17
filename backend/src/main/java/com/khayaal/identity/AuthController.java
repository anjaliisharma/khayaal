package com.khayaal.identity;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final String REFRESH_COOKIE = "khayaal_refresh";
    private final AuthService service;
    private final AccountDeletionService accountDeletion;
    private final long accessTokenSeconds;
    private final boolean secureCookie;

    public AuthController(AuthService service, AccountDeletionService accountDeletion, SecurityProperties properties) {
        this.service = service;
        this.accountDeletion = accountDeletion;
        this.accessTokenSeconds = properties.accessTokenMinutes() * 60;
        this.secureCookie = properties.secureCookie();
    }

    @PostMapping("/register")
    TokenResponse register(@Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        return respond(service.register(request.email(), request.password(), request.displayName()), response);
    }

    @PostMapping("/login")
    TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        return respond(service.login(request.email(), request.password()), response);
    }

    @PostMapping("/refresh")
    TokenResponse refresh(@CookieValue(REFRESH_COOKIE) String refreshToken, HttpServletResponse response) {
        return respond(service.refresh(refreshToken), response);
    }

    @PostMapping("/logout")
    void logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken, HttpServletResponse response) {
        service.revoke(refreshToken);
        response.addHeader("Set-Cookie", cookie("", 0).toString());
    }

    @DeleteMapping("/account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteAccount(@RequestParam String confirmation, Authentication authentication, HttpServletResponse response) {
        if (!"DELETE".equals(confirmation)) throw new IllegalArgumentException("Type DELETE to confirm account deletion");
        accountDeletion.delete(CurrentUser.require(authentication).userId());
        response.addHeader("Set-Cookie", cookie("", 0).toString());
    }

    private TokenResponse respond(AuthService.AuthResult result, HttpServletResponse response) {
        response.addHeader("Set-Cookie", cookie(result.refreshToken(), result.refreshLifetime().toSeconds()).toString());
        return new TokenResponse(result.accessToken(), "Bearer", accessTokenSeconds);
    }

    private ResponseCookie cookie(String value, long maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value).httpOnly(true).sameSite("Strict").secure(secureCookie)
                .path("/api/auth").maxAge(maxAge).build();
    }

    record RegisterRequest(@Email @NotBlank String email, @NotBlank @Size(min=10,max=100) String password, @NotBlank @Size(max=100) String displayName) { }
    record LoginRequest(@Email @NotBlank String email, @NotBlank String password) { }
    record TokenResponse(String accessToken, String tokenType, long expiresInSeconds) { }
}
