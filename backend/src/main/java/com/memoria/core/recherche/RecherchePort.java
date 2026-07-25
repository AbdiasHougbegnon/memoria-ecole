package com.memoria.core.recherche;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface RecherchePort {

    // vecteurs[i] doit correspondre a segments.get(i) -- meme ordre, meme taille.
    void indexerSegments(
            UUID sessionId,
            String titreSession,
            Instant dateSession,
            List<SegmentARecherche> segments,
            List<float[]> vecteurs
    );

    List<ResultatRecherche> rechercher(String texteRequete, float[] vecteurRequete, int limite);

    // Supprime tous les documents indexes d'une session (droit a l'effacement,
    // voir SessionPurgeService). Peut lever RechercheException : c'est
    // l'appelant qui traite cet appel comme best-effort, pas cette implementation.
    void supprimerDocumentsSession(UUID sessionId);
}
