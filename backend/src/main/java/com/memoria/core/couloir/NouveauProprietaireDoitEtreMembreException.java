package com.memoria.core.couloir;

import java.util.UUID;

public class NouveauProprietaireDoitEtreMembreException extends RuntimeException {

    public NouveauProprietaireDoitEtreMembreException(UUID couloirId, UUID utilisateurId) {
        super("L'utilisateur " + utilisateurId + " doit etre membre du couloir " + couloirId + " avant de pouvoir en devenir proprietaire");
    }
}
