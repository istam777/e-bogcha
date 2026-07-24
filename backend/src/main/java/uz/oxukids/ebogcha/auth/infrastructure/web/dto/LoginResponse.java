package uz.oxukids.ebogcha.auth.infrastructure.web.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        int expiresIn,
        CurrentUserResponse user
) {
    public LoginResponse(String accessToken, int expiresIn, CurrentUserResponse user) {
        this(accessToken, "Bearer", expiresIn, user);
    }
}
