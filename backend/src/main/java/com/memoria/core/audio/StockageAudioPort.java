package com.memoria.core.audio;

import java.util.UUID;

// Detail d'infrastructure remplacable (fichier local aujourd'hui, Azure Blob demain).
public interface StockageAudioPort {

    String sauvegarder(UUID sessionId, int numeroSequence, byte[] donnees);

    // Relit un chunk deja sauvegarde (ex: extraction audio pour la
    // reconnaissance de locuteur recurrente, voir IdentificationLocuteurService).
    byte[] lire(String cheminStockage);

    // Supprime tous les chunks d'une session. Peut lever StockageAudioException :
    // c'est l'appelant (SessionPurgeService.nettoyerDependancesExternes) qui
    // traite cet appel comme best-effort, pas cette implementation.
    void supprimerSession(UUID sessionId);
}
