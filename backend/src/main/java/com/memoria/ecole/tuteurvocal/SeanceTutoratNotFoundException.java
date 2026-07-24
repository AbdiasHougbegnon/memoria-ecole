package com.memoria.ecole.tuteurvocal;

import java.util.UUID;

public class SeanceTutoratNotFoundException extends RuntimeException {

    public SeanceTutoratNotFoundException(UUID id) {
        super("Seance de tutorat introuvable : " + id);
    }
}
