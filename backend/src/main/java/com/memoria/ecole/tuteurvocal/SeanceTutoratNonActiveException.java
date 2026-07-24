package com.memoria.ecole.tuteurvocal;

import java.util.UUID;

public class SeanceTutoratNonActiveException extends RuntimeException {

    public SeanceTutoratNonActiveException(UUID id) {
        super("La seance de tutorat " + id + " n'est plus en cours");
    }
}
