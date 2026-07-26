package com.memoria.ecole.notion;

import com.memoria.core.couloir.Couloir;
import com.memoria.core.couloir.CouloirService;
import com.memoria.core.couloir.PasProprietaireDuCouloirException;
import com.memoria.ecole.matiere.Matiere;
import com.memoria.ecole.matiere.MatiereNotFoundException;
import com.memoria.ecole.matiere.MatiereService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotionCandidateServiceTest {

    @Mock
    private NotionCandidateRepository notionCandidateRepository;

    @Mock
    private NotionRepository notionRepository;

    @Mock
    private NotionService notionService;

    @Mock
    private MatiereService matiereService;

    @Mock
    private CouloirService couloirService;

    private NotionCandidateService notionCandidateService;

    @BeforeEach
    void setUp() {
        notionCandidateService = new NotionCandidateService(
                notionCandidateRepository, notionRepository, notionService, matiereService, couloirService
        );
    }

    @Test
    void listerCandidates_retourne_les_candidates_de_la_matiere() {
        UUID matiereId = UUID.randomUUID();
        when(matiereService.obtenirMatiere(matiereId)).thenReturn(new Matiere("Maths", UUID.randomUUID(), UUID.randomUUID()));
        List<NotionCandidate> candidates = List.of(new NotionCandidate(UUID.randomUUID(), matiereId, "Derivee", "def"));
        when(notionCandidateRepository.findByMatiereIdOrderByDateCreationAsc(matiereId)).thenReturn(candidates);

        List<NotionCandidate> resultat = notionCandidateService.listerCandidates(matiereId);

        assertThat(resultat).isEqualTo(candidates);
    }

    @Test
    void listerCandidates_leve_une_exception_si_la_matiere_est_introuvable() {
        UUID matiereId = UUID.randomUUID();
        when(matiereService.obtenirMatiere(matiereId)).thenThrow(new MatiereNotFoundException(matiereId));

        assertThatThrownBy(() -> notionCandidateService.listerCandidates(matiereId))
                .isInstanceOf(MatiereNotFoundException.class);
    }

    @Test
    void validerCandidate_cree_la_notion_a_la_suite_des_existantes_et_marque_la_candidate_validee() {
        UUID matiereId = UUID.randomUUID();
        UUID documentMatiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        NotionCandidate candidate = new NotionCandidate(documentMatiereId, matiereId, "Derivee", "brouillon IA");
        Notion notionExistante = new Notion(matiereId, "Integrale", "def", 0);
        Notion notionCreee = new Notion(matiereId, "Derivee", "Taux de variation instantane", 1, documentMatiereId);
        when(notionCandidateRepository.findById(candidate.getId())).thenReturn(Optional.of(candidate));
        when(notionRepository.findByMatiereIdOrderByOrdreAsc(matiereId)).thenReturn(List.of(notionExistante));
        when(notionService.creerNotionValidee(matiereId, "Derivee", "Taux de variation instantane", 1, documentMatiereId, utilisateurId))
                .thenReturn(notionCreee);

        Notion resultat = notionCandidateService.validerCandidate(candidate.getId(), "Derivee", "Taux de variation instantane", utilisateurId);

        assertThat(resultat).isEqualTo(notionCreee);
        assertThat(candidate.getStatut()).isEqualTo(StatutNotionCandidate.VALIDEE);
        verify(notionCandidateRepository).save(candidate);
    }

    @Test
    void validerCandidate_ne_modifie_pas_la_candidate_si_pas_proprietaire_du_couloir() {
        UUID matiereId = UUID.randomUUID();
        UUID documentMatiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        NotionCandidate candidate = new NotionCandidate(documentMatiereId, matiereId, "Derivee", "brouillon IA");
        when(notionCandidateRepository.findById(candidate.getId())).thenReturn(Optional.of(candidate));
        when(notionRepository.findByMatiereIdOrderByOrdreAsc(matiereId)).thenReturn(List.of());
        when(notionService.creerNotionValidee(any(), any(), any(), anyInt(), any(), any()))
                .thenThrow(new PasProprietaireDuCouloirException(matiereId, utilisateurId));

        assertThatThrownBy(() -> notionCandidateService.validerCandidate(candidate.getId(), "Derivee", "def", utilisateurId))
                .isInstanceOf(PasProprietaireDuCouloirException.class);
        assertThat(candidate.getStatut()).isEqualTo(StatutNotionCandidate.EN_ATTENTE);
        verify(notionCandidateRepository, never()).save(any());
    }

    @Test
    void validerCandidate_leve_une_exception_si_la_candidate_est_introuvable() {
        UUID id = UUID.randomUUID();
        when(notionCandidateRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notionCandidateService.validerCandidate(id, "Terme", "Def", UUID.randomUUID()))
                .isInstanceOf(NotionCandidateNotFoundException.class);
    }

    @Test
    void rejeterCandidate_marque_la_candidate_rejetee_si_proprietaire_du_couloir() {
        UUID proprietaireId = UUID.randomUUID();
        UUID couloirId = UUID.randomUUID();
        UUID matiereId = UUID.randomUUID();
        NotionCandidate candidate = new NotionCandidate(UUID.randomUUID(), matiereId, "Derivee", "brouillon IA");
        when(notionCandidateRepository.findById(candidate.getId())).thenReturn(Optional.of(candidate));
        when(matiereService.obtenirMatiere(matiereId)).thenReturn(new Matiere("Maths", couloirId, proprietaireId));
        when(couloirService.obtenirCouloir(couloirId)).thenReturn(new Couloir("Ing1-SI EPISEN", proprietaireId));
        when(notionCandidateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        NotionCandidate resultat = notionCandidateService.rejeterCandidate(candidate.getId(), proprietaireId);

        assertThat(resultat.getStatut()).isEqualTo(StatutNotionCandidate.REJETEE);
    }

    @Test
    void rejeterCandidate_leve_une_exception_si_pas_proprietaire_du_couloir() {
        UUID proprietaireId = UUID.randomUUID();
        UUID couloirId = UUID.randomUUID();
        UUID matiereId = UUID.randomUUID();
        NotionCandidate candidate = new NotionCandidate(UUID.randomUUID(), matiereId, "Derivee", "brouillon IA");
        when(notionCandidateRepository.findById(candidate.getId())).thenReturn(Optional.of(candidate));
        when(matiereService.obtenirMatiere(matiereId)).thenReturn(new Matiere("Maths", couloirId, proprietaireId));
        when(couloirService.obtenirCouloir(couloirId)).thenReturn(new Couloir("Ing1-SI EPISEN", proprietaireId));

        assertThatThrownBy(() -> notionCandidateService.rejeterCandidate(candidate.getId(), UUID.randomUUID()))
                .isInstanceOf(PasProprietaireDuCouloirException.class);
        assertThat(candidate.getStatut()).isEqualTo(StatutNotionCandidate.EN_ATTENTE);
        verify(notionCandidateRepository, never()).save(any());
    }

    @Test
    void rejeterCandidate_leve_une_exception_si_la_candidate_est_introuvable() {
        UUID id = UUID.randomUUID();
        when(notionCandidateRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notionCandidateService.rejeterCandidate(id, UUID.randomUUID()))
                .isInstanceOf(NotionCandidateNotFoundException.class);
    }
}
