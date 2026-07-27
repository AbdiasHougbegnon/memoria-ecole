package com.memoria.entreprise.gouvernance;

import com.memoria.core.gouvernance.EffaceurDonneesUtilisateurPort;
import com.memoria.core.gouvernance.ExportDonneesUtilisateur;
import com.memoria.core.gouvernance.ExportateurDonneesUtilisateurPort;
import com.memoria.core.gouvernance.PurgeurDonneesSessionPort;
import com.memoria.entreprise.compterendu.CompteRendu;
import com.memoria.entreprise.compterendu.CompteRenduRepository;
import com.memoria.entreprise.engagement.Engagement;
import com.memoria.entreprise.engagement.EngagementRepository;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Part Entreprise de la gouvernance des donnees -- implemente les ports
// core.gouvernance.* pour que le moteur n'ait jamais a importer de type
// Entreprise (voir audit du 2026-07-27, docs/phases/phase-13-gouvernance-donnees.md).
// Miroir exact de la logique qui vivait avant dans
// GouvernanceDonneesService/SessionPurgeService.
@Component
public class EntrepriseGouvernanceContributor
        implements EffaceurDonneesUtilisateurPort, ExportateurDonneesUtilisateurPort, PurgeurDonneesSessionPort {

    private final EngagementRepository engagementRepository;
    private final CompteRenduRepository compteRenduRepository;

    public EntrepriseGouvernanceContributor(EngagementRepository engagementRepository, CompteRenduRepository compteRenduRepository) {
        this.engagementRepository = engagementRepository;
        this.compteRenduRepository = compteRenduRepository;
    }

    @Override
    public void effacerDonneesUtilisateur(UUID utilisateurId) {
        engagementRepository.anonymiserResponsable(utilisateurId);
    }

    @Override
    public void purgerDonneesSession(UUID sessionId) {
        compteRenduRepository.deleteBySessionId(sessionId);
        engagementRepository.deleteBySessionId(sessionId);
    }

    @Override
    public List<ExportDonneesUtilisateur.EngagementExporte> exporterEngagements(UUID utilisateurId) {
        return engagementRepository.findByResponsableUtilisateurId(utilisateurId).stream()
                .map((Engagement e) -> new ExportDonneesUtilisateur.EngagementExporte(
                        e.getId(), e.getSessionId(), e.getDescription(), e.getEcheance(), e.getStatut().name()))
                .toList();
    }

    @Override
    public Optional<String> exporterCompteRenduSession(UUID sessionId) {
        return compteRenduRepository.findBySessionId(sessionId).map(CompteRendu::getSynthese);
    }
}
