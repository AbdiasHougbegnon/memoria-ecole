package com.memoria.ecole.qcm;

import java.util.UUID;

public class QcmMatiereNotFoundException extends RuntimeException {

    public QcmMatiereNotFoundException(UUID matiereId) {
        super("Aucun QCM de matiere trouve pour la matiere " + matiereId);
    }
}
