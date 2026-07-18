package com.memoria.core.auth;

import jakarta.validation.constraints.NotBlank;

public record ConnexionRequest(
        @NotBlank String email,
        @NotBlank String motDePasse
) {
}
