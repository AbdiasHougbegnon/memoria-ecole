package com.memoria.core.couloir;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class CouloirService {

    private final CouloirRepository couloirRepository;
    private final MembreCouloirRepository membreCouloirRepository;

    public CouloirService(CouloirRepository couloirRepository, MembreCouloirRepository membreCouloirRepository) {
        this.couloirRepository = couloirRepository;
        this.membreCouloirRepository = membreCouloirRepository;
    }

    public Couloir creerCouloir(String nom, UUID proprietaireId) {
        Couloir couloir = couloirRepository.save(new Couloir(nom, proprietaireId));
        // Le createur devient automatiquement membre de son propre couloir.
        membreCouloirRepository.save(new MembreCouloir(couloir.getId(), proprietaireId));
        return couloir;
    }

    // Idempotent : rejoindre un couloir dont on est deja membre ne fait rien
    // (pas d'erreur, pas de doublon) -- coherent avec "sans gestion de liste
    // manuelle" du master prompt.
    public Couloir rejoindreCouloir(UUID couloirId, UUID utilisateurId) {
        Couloir couloir = obtenirCouloir(couloirId);
        if (!membreCouloirRepository.existsByCouloirIdAndUtilisateurId(couloirId, utilisateurId)) {
            membreCouloirRepository.save(new MembreCouloir(couloirId, utilisateurId));
        }
        return couloir;
    }

    public boolean estMembre(UUID couloirId, UUID utilisateurId) {
        return membreCouloirRepository.existsByCouloirIdAndUtilisateurId(couloirId, utilisateurId);
    }

    public List<Couloir> listerMesCouloirs(UUID utilisateurId) {
        return membreCouloirRepository.findByUtilisateurId(utilisateurId).stream()
                .map(membre -> obtenirCouloir(membre.getCouloirId()))
                .toList();
    }

    public long compterMembres(UUID couloirId) {
        return membreCouloirRepository.countByCouloirId(couloirId);
    }

    public Couloir obtenirCouloir(UUID id) {
        return couloirRepository.findById(id)
                .orElseThrow(() -> new CouloirNotFoundException(id));
    }
}
