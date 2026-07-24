package com.memoria.ecole.seance;

import java.util.UUID;

public class SeanceNotFoundException extends RuntimeException {

    public SeanceNotFoundException(UUID id) {
        super("Seance introuvable : " + id);
    }
}
