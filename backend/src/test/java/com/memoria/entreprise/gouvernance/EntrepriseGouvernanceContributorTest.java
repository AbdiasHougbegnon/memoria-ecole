package com.memoria.entreprise.gouvernance;

import com.memoria.core.gouvernance.ExportDonneesUtilisateur;
import com.memoria.entreprise.compterendu.CompteRendu;
import com.memoria.entreprise.compterendu.CompteRenduRepository;
import com.memoria.entreprise.compterendu.StatutCompteRendu;
import com.memoria.entreprise.engagement.Engagement;
import com.memoria.entreprise.engagement.EngagementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntrepriseGouvernanceContributorTest {

    @Mock private EngagementRepository engagementRepository;
    @Mock private CompteRenduRepository compteRenduRepository;

    private EntrepriseGouvernanceContributor contributor;

    @BeforeEach
    void setUp() {
        contributor = new EntrepriseGouvernanceContributor(engagementRepository, compteRenduRepository);
    }

    @Test
    void effacerDonneesUtilisateur_anonymise_le_responsable_des_engagements() {
        UUID utilisateurId = UUID.randomUUID();

        contributor.effacerDonneesUtilisateur(utilisateurId);

        verify(engagementRepository).anonymiserResponsable(utilisateurId);
    }

    @Test
    void purgerDonneesSession_supprime_le_compte_rendu_et_les_engagements() {
        UUID sessionId = UUID.randomUUID();

        contributor.purgerDonneesSession(sessionId);

        verify(compteRenduRepository).deleteBySessionId(sessionId);
        verify(engagementRepository).deleteBySessionId(sessionId);
    }

    @Test
    void exporterEngagements_projette_la_description_lecheance_et_le_statut() {
        UUID utilisateurId = UUID.randomUUID();
        Engagement engagement = new Engagement(UUID.randomUUID(), "Rediger le compte-rendu", "Jean", "2026-08-01", utilisateurId);
        when(engagementRepository.findByResponsableUtilisateurId(utilisateurId)).thenReturn(List.of(engagement));

        List<ExportDonneesUtilisateur.EngagementExporte> export = contributor.exporterEngagements(utilisateurId);

        assertThat(export).hasSize(1);
        assertThat(export.get(0).description()).isEqualTo("Rediger le compte-rendu");
        assertThat(export.get(0).echeance()).isEqualTo("2026-08-01");
    }

    @Test
    void exporterCompteRenduSession_absent_si_aucun_compte_rendu() {
        UUID sessionId = UUID.randomUUID();
        when(compteRenduRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());

        assertThat(contributor.exporterCompteRenduSession(sessionId)).isEmpty();
    }

    @Test
    void exporterCompteRenduSession_retourne_la_synthese() {
        UUID sessionId = UUID.randomUUID();
        CompteRendu compteRendu = new CompteRendu(sessionId, "Synthese de la reunion", List.of(), List.of(), List.of(), StatutCompteRendu.REUSSI);
        when(compteRenduRepository.findBySessionId(sessionId)).thenReturn(Optional.of(compteRendu));

        assertThat(contributor.exporterCompteRenduSession(sessionId)).contains("Synthese de la reunion");
    }
}
