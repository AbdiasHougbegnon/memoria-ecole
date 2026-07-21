package com.memoria.core.locuteur;

import java.time.Instant;
import java.util.UUID;

public record EmpreinteVocaleResponse(UUID id, StatutEmpreinteVocale statut, Instant dateConsentement) {

    public static EmpreinteVocaleResponse depuis(EmpreinteVocale empreinte) {
        return new EmpreinteVocaleResponse(empreinte.getId(), empreinte.getStatut(), empreinte.getDateConsentement());
    }

    // Aucune empreinte enrolee -- pas un 404 (l'utilisateur existe bien),
    // simplement un etat "rien a afficher" pour le frontend.
    public static EmpreinteVocaleResponse absente() {
        return new EmpreinteVocaleResponse(null, null, null);
    }
}
