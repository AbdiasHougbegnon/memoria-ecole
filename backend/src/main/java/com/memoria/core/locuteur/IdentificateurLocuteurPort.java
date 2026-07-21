package com.memoria.core.locuteur;

import java.util.List;

// Detail d'infrastructure remplacable (Azure Speaker Recognition
// aujourd'hui, un modele auto-heberge demain -- meme principe que
// TranscripteurPort pour la transcription).
public interface IdentificateurLocuteurPort {

    // Enregistre un nouveau profil vocal a partir d'un echantillon audio de
    // consentement, renvoie l'identifiant du profil chez le fournisseur.
    String enroller(byte[] audioConsentement);

    // Best-effort : ne doit pas empecher la revocation locale si l'appel
    // distant echoue (voir EmpreinteVocaleService.revoquer).
    void supprimerProfil(String profilExterneId);

    // Compare un segment audio aux profils candidats, renvoie le profil le
    // plus probable et sa confiance (ResultatIdentification.aucunMatch() si
    // aucun candidat ne correspond).
    ResultatIdentification identifier(byte[] audioSegment, List<String> profilsExternesCandidats);
}
