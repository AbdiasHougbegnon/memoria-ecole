package com.memoria.core.session;

import jakarta.validation.constraints.NotBlank;

public record CreateSessionRequest(
        @NotBlank(message = "le titre est obligatoire")
        String titre
) {
}
