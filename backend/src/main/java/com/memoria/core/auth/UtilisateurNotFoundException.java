package com.memoria.core.auth;

import java.util.UUID;

public class UtilisateurNotFoundException extends RuntimeException {

    public UtilisateurNotFoundException(UUID id) {
        super("Aucun utilisateur trouve pour l'id " + id);
    }
}
