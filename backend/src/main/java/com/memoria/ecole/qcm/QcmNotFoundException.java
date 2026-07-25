package com.memoria.ecole.qcm;

import java.util.UUID;

public class QcmNotFoundException extends RuntimeException {

    public QcmNotFoundException(UUID sessionId) {
        super("Aucun QCM disponible pour la session " + sessionId);
    }
}
