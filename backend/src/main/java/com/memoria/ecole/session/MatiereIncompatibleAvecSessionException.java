package com.memoria.ecole.session;

import java.util.UUID;

public class MatiereIncompatibleAvecSessionException extends RuntimeException {

    public MatiereIncompatibleAvecSessionException(UUID matiereId, UUID sessionId) {
        super("La matiere " + matiereId + " n'appartient pas au couloir de la session " + sessionId);
    }
}
