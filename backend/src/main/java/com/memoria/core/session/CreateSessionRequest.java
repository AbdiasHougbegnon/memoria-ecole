package com.memoria.core.session;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CreateSessionRequest(
        @NotBlank(message = "le titre est obligatoire")
        String titre,
        UUID couloirId,
        boolean consentementEnregistrement
) {
}
