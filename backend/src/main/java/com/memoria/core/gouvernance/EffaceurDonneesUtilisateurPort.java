package com.memoria.core.gouvernance;

import java.util.UUID;

// Implemente par chaque produit (Ecole, Entreprise) pour purger/anonymiser
// ses propres donnees liees a un utilisateur lors d'un effacement de compte
// (RGPD) -- le moteur orchestre (voir GouvernanceDonneesService.effacerCompte)
// sans jamais importer de type produit concret. Beans Spring collectes
// automatiquement via List<EffaceurDonneesUtilisateurPort> ; voir audit du
// 2026-07-27 (core.gouvernance importait directement des classes ecole et
// entreprise avant cette extraction).
public interface EffaceurDonneesUtilisateurPort {

    void effacerDonneesUtilisateur(UUID utilisateurId);
}
