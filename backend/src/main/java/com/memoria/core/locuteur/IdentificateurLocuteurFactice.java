package com.memoria.core.locuteur;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Implementation de verification uniquement -- jamais active par defaut ni
// en production (profil Spring dedie). Ne pretend pas reconnaitre une voix
// avec un vrai modele ML (impossible a verifier sans credentials Azure
// reels) : le but est de prouver que tout le reste du chemin fonctionne
// reellement (consentement enregistre, audio decoupe par locuteur et par
// chunk depuis de vrais fichiers stockes, un appel par (chunk, locuteur),
// ecriture jusqu'a l'UI) avec un signal deterministe mais tire de l'audio
// reel : la longueur en octets de l'echantillon enrole, comparee a celle du
// segment a identifier. Voir la doc de cette brique pour le protocole de
// verification complet.
@Component
@Profile("verification-locuteur")
public class IdentificateurLocuteurFactice implements IdentificateurLocuteurPort {

    private final Map<String, Integer> tailleEnrolementParProfil = new ConcurrentHashMap<>();

    @Override
    public String enroller(byte[] audioConsentement) {
        String profilId = UUID.randomUUID().toString();
        tailleEnrolementParProfil.put(profilId, audioConsentement.length);
        return profilId;
    }

    @Override
    public void supprimerProfil(String profilExterneId) {
        tailleEnrolementParProfil.remove(profilExterneId);
    }

    @Override
    public ResultatIdentification identifier(byte[] audioSegment, List<String> profilsExternesCandidats) {
        if (profilsExternesCandidats.isEmpty()) {
            return ResultatIdentification.aucunMatch();
        }

        String meilleurProfil = null;
        int meilleurEcart = Integer.MAX_VALUE;
        for (String profil : profilsExternesCandidats) {
            Integer tailleEnrolee = tailleEnrolementParProfil.get(profil);
            if (tailleEnrolee == null) {
                continue;
            }
            int ecart = Math.abs(tailleEnrolee - audioSegment.length);
            if (ecart < meilleurEcart) {
                meilleurEcart = ecart;
                meilleurProfil = profil;
            }
        }

        return meilleurProfil == null ? ResultatIdentification.aucunMatch() : new ResultatIdentification(meilleurProfil, 0.99);
    }
}
