package com.memoria.core.couloir;

import java.util.UUID;

public class CouloirNotFoundException extends RuntimeException {

    public CouloirNotFoundException(UUID id) {
        super("Couloir introuvable : " + id);
    }
}
