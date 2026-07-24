package uz.oxukids.ebogcha.auth.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 100) String login,
        @NotBlank @Size(max = 100) String password
) {
}
