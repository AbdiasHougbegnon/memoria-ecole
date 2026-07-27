package com.memoria.core.gouvernance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Implemente par chaque produit pour fournir sa part de l'export RGPD d'un
// utilisateur (voir GouvernanceDonneesService.exporterDonnees) -- le moteur
// agrege les fragments sans importer de type produit concret. Methodes par
// defaut a liste/optionnel vide : chaque produit ne redefinit que celles qui
// le concernent (voir EcoleGouvernanceContributor, EntrepriseGouvernanceContributor).
// Voir audit du 2026-07-27.
public interface ExportateurDonneesUtilisateurPort {

    default List<ExportDonneesUtilisateur.SeanceTutoratExportee> exporterSeancesTutorat(UUID utilisateurId) {
        return List.of();
    }

    default List<ExportDonneesUtilisateur.MaitriseNotionExportee> exporterMaitrises(UUID utilisateurId) {
        return List.of();
    }

    default List<ExportDonneesUtilisateur.TentativeQcmExportee> exporterTentativesQcm(UUID utilisateurId) {
        return List.of();
    }

    default List<ExportDonneesUtilisateur.EngagementExporte> exporterEngagements(UUID utilisateurId) {
        return List.of();
    }

    // Enrichissement par session (pas par utilisateur) : au plus un produit
    // repond present pour une session donnee (Ecole XOR Entreprise), voir
    // GouvernanceDonneesService.exporterSession qui prend le premier resultat
    // non vide.
    default Optional<String> exporterResumeCoursSession(UUID sessionId) {
        return Optional.empty();
    }

    default Optional<String> exporterCompteRenduSession(UUID sessionId) {
        return Optional.empty();
    }
}
