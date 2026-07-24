package com.memoria.ecole.matiere;

import java.util.UUID;

public class MatiereNotFoundException extends RuntimeException {

    public MatiereNotFoundException(UUID id) {
        super("Matiere introuvable : " + id);
    }
}
