package com.memoria.core.couloir;

import java.util.UUID;

public class PasMembreDuCouloirException extends RuntimeException {

    public PasMembreDuCouloirException(UUID couloirId, UUID utilisateurId) {
        super("L'utilisateur " + utilisateurId + " n'est pas membre du couloir " + couloirId);
    }
}
