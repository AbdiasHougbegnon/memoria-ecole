package com.memoria.core.couloir;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public Couloir renommerCouloir(UUID couloirId, String nouveauNom, UUID utilisateurId) {
        Couloir couloir = obtenirCouloir(couloirId);
        verifierProprietaire(couloir, utilisateurId);
        couloir.renommer(nouveauNom);
        return couloirRepository.save(couloir);
    }

    // Deux suppressions derivees (membres, puis couloir) : une transaction
    // est necessaire, sinon deleteByCouloirId (requete derivee, pas heritee
    // de JpaRepository) echoue avec TransactionRequiredException.
    @Transactional
    public void supprimerCouloir(UUID couloirId, UUID utilisateurId) {
        Couloir couloir = obtenirCouloir(couloirId);
        verifierProprietaire(couloir, utilisateurId);
        membreCouloirRepository.deleteByCouloirId(couloirId);
        couloirRepository.deleteById(couloirId);
        // Les sessions deja rattachees (session.couloirId) ne sont pas modifiees :
        // reference brute sans contrainte FK, comme partout ailleurs dans le
        // projet. Elles redeviennent visibles uniquement pour leur createur
        // (plus personne n'est membre du couloir supprime).
    }

    @Transactional
    public void retirerMembre(UUID couloirId, UUID membreARetirerId, UUID utilisateurId) {
        Couloir couloir = obtenirCouloir(couloirId);
        verifierProprietaire(couloir, utilisateurId);
        if (membreARetirerId.equals(couloir.getProprietaireId())) {
            throw new ProprietaireNePeutPasSeRetirerException(couloirId);
        }
        membreCouloirRepository.deleteByCouloirIdAndUtilisateurId(couloirId, membreARetirerId);
    }

    public List<MembreCouloir> listerMembres(UUID couloirId) {
        return membreCouloirRepository.findByCouloirId(couloirId);
    }

    // Idempotent comme rejoindreCouloir : quitter un couloir dont on n'est
    // pas (ou plus) membre ne fait rien, pas d'erreur -- deleteByCouloirIdAndUtilisateurId
    // ne supprime alors simplement aucune ligne.
    @Transactional
    public void quitterCouloir(UUID couloirId, UUID utilisateurId) {
        Couloir couloir = obtenirCouloir(couloirId);
        if (utilisateurId.equals(couloir.getProprietaireId())) {
            throw new ProprietaireNePeutPasSeRetirerException(couloirId);
        }
        membreCouloirRepository.deleteByCouloirIdAndUtilisateurId(couloirId, utilisateurId);
    }

    public Couloir transfererPropriete(UUID couloirId, UUID nouveauProprietaireId, UUID utilisateurId) {
        Couloir couloir = obtenirCouloir(couloirId);
        verifierProprietaire(couloir, utilisateurId);
        if (!membreCouloirRepository.existsByCouloirIdAndUtilisateurId(couloirId, nouveauProprietaireId)) {
            throw new NouveauProprietaireDoitEtreMembreException(couloirId, nouveauProprietaireId);
        }
        couloir.transfererPropriete(nouveauProprietaireId);
        return couloirRepository.save(couloir);
    }

    private void verifierProprietaire(Couloir couloir, UUID utilisateurId) {
        if (!couloir.getProprietaireId().equals(utilisateurId)) {
            throw new PasProprietaireDuCouloirException(couloir.getId(), utilisateurId);
        }
    }
}
