package com.memoria.ecole.session;

import java.util.UUID;

public class PasCreateurDeLaSessionException extends RuntimeException {

    public PasCreateurDeLaSessionException(UUID sessionId, UUID utilisateurId) {
        super("L'utilisateur " + utilisateurId + " n'est pas le createur de la session " + sessionId);
    }
}
