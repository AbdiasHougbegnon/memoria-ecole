package com.memoria.core.couloir;

import java.util.UUID;

public class ProprietaireNePeutPasSeRetirerException extends RuntimeException {

    public ProprietaireNePeutPasSeRetirerException(UUID couloirId) {
        super("Le proprietaire du couloir " + couloirId + " ne peut pas se retirer lui-meme");
    }
}
