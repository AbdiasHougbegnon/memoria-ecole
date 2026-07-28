package com.memoria.ecole.exercice;

import java.util.UUID;

public class TexteExtraitIndisponibleException extends RuntimeException {

    public TexteExtraitIndisponibleException(UUID travailId) {
        super("Aucun texte extrait disponible pour corriger le travail papier " + travailId);
    }
}
