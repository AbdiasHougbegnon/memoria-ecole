package com.memoria.ecole.tuteurvocal;

import java.util.UUID;

public class TourDialogueTutoratNotFoundException extends RuntimeException {

    public TourDialogueTutoratNotFoundException(UUID id) {
        super("Tour de dialogue introuvable : " + id);
    }
}
