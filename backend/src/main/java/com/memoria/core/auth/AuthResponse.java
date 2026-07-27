package com.memoria.core.auth;

import java.util.UUID;

public record AuthResponse(String token, UUID utilisateurId, String email, ModuleMemoria module, boolean admin) {

    public static AuthResponse depuis(Utilisateur utilisateur, String token) {
        return new AuthResponse(token, utilisateur.getId(), utilisateur.getEmail(), utilisateur.getModule(), utilisateur.estAdmin());
    }
}
