package com.memoria.entreprise.compterendu;

import java.util.UUID;

public class CompteRenduNotFoundException extends RuntimeException {

    public CompteRenduNotFoundException(UUID sessionId) {
        super("Aucun compte rendu disponible pour la session " + sessionId);
    }
}
