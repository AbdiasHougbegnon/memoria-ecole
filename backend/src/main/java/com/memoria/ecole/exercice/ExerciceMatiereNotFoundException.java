package com.memoria.ecole.exercice;

import java.util.UUID;

public class ExerciceMatiereNotFoundException extends RuntimeException {

    public ExerciceMatiereNotFoundException(UUID matiereId) {
        super("Aucun exercice trouve pour la matiere " + matiereId);
    }
}
