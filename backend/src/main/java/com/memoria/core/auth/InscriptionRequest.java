package com.memoria.core.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InscriptionRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String motDePasse,
        @NotNull(message = "le module est obligatoire") ModuleMemoria module
) {
}
