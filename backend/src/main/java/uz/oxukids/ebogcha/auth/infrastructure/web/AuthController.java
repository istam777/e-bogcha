package uz.oxukids.ebogcha.auth.infrastructure.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.oxukids.ebogcha.auth.application.service.GetCurrentUserService;
import uz.oxukids.ebogcha.auth.application.service.LoginService;
import uz.oxukids.ebogcha.auth.application.service.LogoutService;
import uz.oxukids.ebogcha.auth.application.service.RefreshTokenService;
import uz.oxukids.ebogcha.auth.infrastructure.web.dto.CurrentUserResponse;
import uz.oxukids.ebogcha.auth.infrastructure.web.dto.LoginRequest;
import uz.oxukids.ebogcha.auth.infrastructure.web.dto.LoginResponse;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "ebogcha_refresh_token";

    private final LoginService loginService;
    private final RefreshTokenService refreshTokenService;
    private final LogoutService logoutService;
    private final GetCurrentUserService getCurrentUserService;

    @Value("${auth.cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${auth.jwt.access-expiration-seconds:900}")
    private int accessExpirationSeconds;

    @Value("${auth.jwt.refresh-expiration-seconds:2592000}")
    private int refreshExpirationSeconds;

    public AuthController(
            LoginService loginService,
            RefreshTokenService refreshTokenService,
            LogoutService logoutService,
            GetCurrentUserService getCurrentUserService
    ) {
        this.loginService = loginService;
        this.refreshTokenService = refreshTokenService;
        this.logoutService = logoutService;
        this.getCurrentUserService = getCurrentUserService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        var result = loginService.login(request.login(), request.password());

        Cookie refreshCookie = createRefreshCookie(result.refreshToken(), cookieSecure, refreshExpirationSeconds);
        response.addCookie(refreshCookie);

        CurrentUserResponse user = getCurrentUserService.getCurrentUser(result.userId());

        return ResponseEntity.ok(new LoginResponse(
                result.accessToken(),
                accessExpirationSeconds,
                user
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @org.springframework.web.bind.annotation.CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response
    ) {
        var result = refreshTokenService.refresh(refreshToken);

        Cookie refreshCookie = createRefreshCookie(result.refreshToken(), cookieSecure, refreshExpirationSeconds);
        response.addCookie(refreshCookie);

        CurrentUserResponse user = getCurrentUserService.getCurrentUser(result.userId());

        return ResponseEntity.ok(new LoginResponse(
                result.accessToken(),
                accessExpirationSeconds,
                user
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @org.springframework.web.bind.annotation.CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response
    ) {
        logoutService.logout(refreshToken);

        Cookie clearCookie = clearRefreshCookie(cookieSecure);
        response.addCookie(clearCookie);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> me(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        CurrentUserResponse user = getCurrentUserService.getCurrentUser(userId);
        return ResponseEntity.ok(user);
    }

    static Cookie createRefreshCookie(String token, boolean secure, int maxAge) {
        Cookie cookie = new Cookie(REFRESH_COOKIE_NAME, token);
        cookie.setHttpOnly(true);
        cookie.setSecure(secure);
        cookie.setPath("/api/v1/auth");
        cookie.setMaxAge(maxAge);
        cookie.setAttribute("SameSite", "Strict");
        return cookie;
    }

    static Cookie clearRefreshCookie(boolean secure) {
        Cookie cookie = new Cookie(REFRESH_COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(secure);
        cookie.setPath("/api/v1/auth");
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "Strict");
        return cookie;
    }
}
