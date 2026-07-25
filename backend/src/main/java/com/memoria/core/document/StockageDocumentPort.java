package com.memoria.core.document;

import java.util.UUID;

// Detail d'infrastructure remplacable (fichier local aujourd'hui, Azure Blob demain).
public interface StockageDocumentPort {

    String sauvegarder(UUID sessionId, String nomFichier, byte[] contenu);

    // Supprime tous les documents d'une session. Peut lever
    // StockageDocumentException : c'est l'appelant (SessionPurgeService.nettoyerDependancesExternes)
    // qui traite cet appel comme best-effort, pas cette implementation.
    void supprimerSession(UUID sessionId);
}
