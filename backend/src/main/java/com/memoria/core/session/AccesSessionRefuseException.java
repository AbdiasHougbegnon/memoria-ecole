package com.memoria.core.session;

import java.util.UUID;

public class AccesSessionRefuseException extends RuntimeException {

    public AccesSessionRefuseException(UUID sessionId, UUID utilisateurId) {
        super("L'utilisateur " + utilisateurId + " n'a pas acces a la session " + sessionId);
    }
}
