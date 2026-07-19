package com.memoria.core.couloir;

import java.util.UUID;

public class PasProprietaireDuCouloirException extends RuntimeException {

    public PasProprietaireDuCouloirException(UUID couloirId, UUID utilisateurId) {
        super("L'utilisateur " + utilisateurId + " n'est pas proprietaire du couloir " + couloirId);
    }
}
