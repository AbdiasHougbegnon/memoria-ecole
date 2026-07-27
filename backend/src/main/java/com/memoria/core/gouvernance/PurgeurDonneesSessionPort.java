package com.memoria.core.gouvernance;

import java.util.UUID;

// Implemente par chaque produit pour purger ses propres donnees rattachees a
// une session lors d'une purge complete (effacement de compte ou retention,
// voir SessionPurgeService.purgerSessionCompletement) -- meme raisonnement
// que EffaceurDonneesUtilisateurPort, voir audit du 2026-07-27.
public interface PurgeurDonneesSessionPort {

    void purgerDonneesSession(UUID sessionId);
}
