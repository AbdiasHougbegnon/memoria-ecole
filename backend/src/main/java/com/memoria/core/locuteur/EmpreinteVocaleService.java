package com.memoria.core.locuteur;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class EmpreinteVocaleService {

    private static final Logger LOG = LoggerFactory.getLogger(EmpreinteVocaleService.class);
    private static final int TAILLE_MIN_OCTETS = 10_000; // ecarte les enregistrements manifestement trop courts

    private final EmpreinteVocaleRepository empreinteVocaleRepository;
    private final IdentificateurLocuteurPort identificateur;

    public EmpreinteVocaleService(EmpreinteVocaleRepository empreinteVocaleRepository, IdentificateurLocuteurPort identificateur) {
        this.empreinteVocaleRepository = empreinteVocaleRepository;
        this.identificateur = identificateur;
    }

    public EmpreinteVocaleResponse enregistrerConsentement(UUID utilisateurId, byte[] audio, boolean consentement) {
        if (!consentement) {
            throw new ConsentementRequisException();
        }
        if (audio == null || audio.length < TAILLE_MIN_OCTETS) {
            throw new AudioEnrollementInsuffisantException();
        }

        empreinteVocaleRepository.findByUtilisateurId(utilisateurId).ifPresent(existante -> {
            supprimerProfilExterneSiPresent(existante);
            empreinteVocaleRepository.delete(existante);
        });

        EmpreinteVocale empreinte = new EmpreinteVocale(utilisateurId);
        try {
            empreinte.marquerPrete(identificateur.enroller(audio));
        } catch (Exception e) {
            LOG.warn("Echec de l'enrolement vocal pour l'utilisateur {}", utilisateurId, e);
            empreinte.marquerEchec();
        }
        return EmpreinteVocaleResponse.depuis(empreinteVocaleRepository.save(empreinte));
    }

    public EmpreinteVocaleResponse obtenirStatut(UUID utilisateurId) {
        return empreinteVocaleRepository.findByUtilisateurId(utilisateurId)
                .map(EmpreinteVocaleResponse::depuis)
                .orElseGet(EmpreinteVocaleResponse::absente);
    }

    public void revoquer(UUID utilisateurId) {
        empreinteVocaleRepository.findByUtilisateurId(utilisateurId).ifPresent(empreinte -> {
            supprimerProfilExterneSiPresent(empreinte);
            empreinteVocaleRepository.delete(empreinte);
        });
    }

    // Best-effort : un echec de suppression distante ne doit jamais bloquer
    // la revocation locale (l'utilisateur doit pouvoir se desinscrire meme
    // si le fournisseur d'identification est indisponible).
    private void supprimerProfilExterneSiPresent(EmpreinteVocale empreinte) {
        if (empreinte.getProfilExterneId() != null) {
            try {
                identificateur.supprimerProfil(empreinte.getProfilExterneId());
            } catch (Exception e) {
                LOG.warn("Echec de la suppression distante du profil vocal {}", empreinte.getProfilExterneId(), e);
            }
        }
    }
}
