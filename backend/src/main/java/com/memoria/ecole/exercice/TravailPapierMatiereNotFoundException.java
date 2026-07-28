package com.memoria.ecole.exercice;

import java.util.UUID;

public class TravailPapierMatiereNotFoundException extends RuntimeException {

    public TravailPapierMatiereNotFoundException(UUID id) {
        super("Travail papier introuvable : " + id);
    }
}
