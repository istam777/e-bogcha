package uz.oxukids.ebogcha.auth.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import uz.oxukids.ebogcha.auth.domain.exception.AccountDisabledException;
import uz.oxukids.ebogcha.auth.domain.exception.AccountTemporarilyLockedException;
import uz.oxukids.ebogcha.auth.domain.exception.AuthenticationConfigurationException;
import uz.oxukids.ebogcha.auth.domain.exception.InvalidCredentialsException;
import uz.oxukids.ebogcha.auth.domain.exception.RefreshTokenException;
import uz.oxukids.ebogcha.auth.domain.exception.RefreshTokenReusedException;
import uz.oxukids.ebogcha.auth.domain.exception.UnauthenticatedException;

import java.net.URI;
import java.time.Instant;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ProblemDetail> invalidCredentials(
            InvalidCredentialsException exception,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.UNAUTHORIZED,
                "AUTH_INVALID_CREDENTIALS",
                "Invalid credentials",
                "The login or password is incorrect.",
                request
        );
    }

    @ExceptionHandler(AccountDisabledException.class)
    ResponseEntity<ProblemDetail> accountDisabled(
            AccountDisabledException exception,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.FORBIDDEN,
                "AUTH_ACCOUNT_DISABLED",
                "Account disabled",
                "The account has been disabled.",
                request
        );
    }

    @ExceptionHandler(AccountTemporarilyLockedException.class)
    ResponseEntity<ProblemDetail> accountLocked(
            AccountTemporarilyLockedException exception,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.LOCKED,
                "AUTH_ACCOUNT_TEMPORARILY_LOCKED",
                "Account temporarily locked",
                "Too many failed attempts. Try again later.",
                request
        );
    }

    @ExceptionHandler(RefreshTokenException.class)
    ResponseEntity<ProblemDetail> refreshTokenInvalid(
            RefreshTokenException exception,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REFRESH_TOKEN_INVALID",
                "Invalid refresh token",
                "The refresh token is invalid or expired.",
                request
        );
    }

    @ExceptionHandler(RefreshTokenReusedException.class)
    ResponseEntity<ProblemDetail> refreshTokenReused(
            RefreshTokenReusedException exception,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REFRESH_TOKEN_REUSED",
                "Refresh token reused",
                "The refresh token has already been used. Please log in again.",
                request
        );
    }

    @ExceptionHandler(UnauthenticatedException.class)
    ResponseEntity<ProblemDetail> unauthenticated(
            UnauthenticatedException exception,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.UNAUTHORIZED,
                "AUTH_UNAUTHENTICATED",
                "Unauthenticated",
                "Authentication is required.",
                request
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> accessDenied(
            AccessDeniedException exception,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.FORBIDDEN,
                "AUTH_ACCESS_DENIED",
                "Access denied",
                "You do not have permission to access this resource.",
                request
        );
    }

    @ExceptionHandler(AuthenticationConfigurationException.class)
    ResponseEntity<ProblemDetail> configurationError(
            AuthenticationConfigurationException exception,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "AUTH_CONFIGURATION_ERROR",
                "Authentication configuration error",
                "The authentication system is misconfigured.",
                request
        );
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String code,
            String title,
            String detail,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:problem:" + code.toLowerCase().replace('_', '-')));
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(problem);
    }
}
