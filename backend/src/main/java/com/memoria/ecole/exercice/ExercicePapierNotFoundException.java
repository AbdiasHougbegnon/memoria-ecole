package com.memoria.ecole.exercice;

import java.util.UUID;

public class ExercicePapierNotFoundException extends RuntimeException {

    public ExercicePapierNotFoundException(UUID id) {
        super("Exercice papier introuvable : " + id);
    }
}
