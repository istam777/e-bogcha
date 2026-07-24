package uz.oxukids.ebogcha.auth.application.service;

import uz.oxukids.ebogcha.auth.infrastructure.web.dto.CurrentUserResponse;

import java.util.UUID;

public interface GetCurrentUserService {

    CurrentUserResponse getCurrentUser(UUID userId);
}
