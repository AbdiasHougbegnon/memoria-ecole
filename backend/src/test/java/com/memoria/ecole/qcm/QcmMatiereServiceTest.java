package com.memoria.ecole.qcm;

import com.memoria.core.couloir.PasMembreDuCouloirException;
import com.memoria.ecole.matiere.AgregateurContenuMatiereService;
import com.memoria.ecole.matiere.MatiereService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QcmMatiereServiceTest {

    @Mock private QcmMatiereRepository qcmMatiereRepository;
    @Mock private MatiereService matiereService;
    @Mock private AgregateurContenuMatiereService agregateurContenuMatiereService;
    @Mock private GenerateurQcmPort generateurQcm;
    @Mock private TentativeQcmRepository tentativeQcmRepository;

    private QcmMatiereService qcmMatiereService;

    @BeforeEach
    void setUp() {
        qcmMatiereService = new QcmMatiereService(
                qcmMatiereRepository, matiereService, agregateurContenuMatiereService,
                generateurQcm, tentativeQcmRepository
        );
    }

    @Test
    void obtenirOuGenererQcmMatiere_genere_a_partir_du_contenu_agrege() {
        UUID matiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        doNothing().when(matiereService).verifierMembreDuCouloir(matiereId, utilisateurId);
        when(qcmMatiereRepository.findByMatiereId(matiereId)).thenReturn(Optional.empty());
        when(agregateurContenuMatiereService.agregerContenu(matiereId))
                .thenReturn("Synthese de plusieurs cours sur les structures de donnees.");
        when(generateurQcm.genererQcm(any())).thenReturn(new QcmGenere(List.of(
                new QuestionExtraite("Qu'est-ce qu'une pile ?", List.of("LIFO", "FIFO", "Arbre", "Graphe"), 0, "Explication")
        )));
        when(qcmMatiereRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        QcmMatiere resultat = qcmMatiereService.obtenirOuGenererQcmMatiere(matiereId, utilisateurId);

        ArgumentCaptor<QcmMatiere> captor = ArgumentCaptor.forClass(QcmMatiere.class);
        verify(qcmMatiereRepository).save(captor.capture());
        assertThat(captor.getValue().getMatiereId()).isEqualTo(matiereId);
        assertThat(captor.getValue().getQuestions()).hasSize(1);
        assertThat(captor.getValue().getStatut()).isEqualTo(StatutQcm.REUSSI);
        assertThat(resultat).isEqualTo(captor.getValue());
    }

    @Test
    void obtenirOuGenererQcmMatiere_leve_une_exception_si_lutilisateur_nest_pas_membre_du_couloir() {
        UUID matiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        doThrow(new PasMembreDuCouloirException(UUID.randomUUID(), utilisateurId))
                .when(matiereService).verifierMembreDuCouloir(matiereId, utilisateurId);

        assertThatThrownBy(() -> qcmMatiereService.obtenirOuGenererQcmMatiere(matiereId, utilisateurId))
                .isInstanceOf(PasMembreDuCouloirException.class);
        verify(qcmMatiereRepository, never()).save(any());
    }

    @Test
    void obtenirOuGenererQcmMatiere_leve_une_exception_si_aucun_contenu_disponible() {
        UUID matiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        doNothing().when(matiereService).verifierMembreDuCouloir(matiereId, utilisateurId);
        when(qcmMatiereRepository.findByMatiereId(matiereId)).thenReturn(Optional.empty());
        when(agregateurContenuMatiereService.agregerContenu(matiereId)).thenReturn("");

        assertThatThrownBy(() -> qcmMatiereService.obtenirOuGenererQcmMatiere(matiereId, utilisateurId))
                .isInstanceOf(AucunContenuMatiereDisponibleException.class);
        verify(generateurQcm, never()).genererQcm(any());
    }

    @Test
    void obtenirOuGenererQcmMatiere_renvoie_le_qcm_deja_en_cache_sans_regenerer() {
        UUID matiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        QcmMatiere dejaGenere = new QcmMatiere(matiereId, List.of(), StatutQcm.REUSSI);
        doNothing().when(matiereService).verifierMembreDuCouloir(matiereId, utilisateurId);
        when(qcmMatiereRepository.findByMatiereId(matiereId)).thenReturn(Optional.of(dejaGenere));

        QcmMatiere resultat = qcmMatiereService.obtenirOuGenererQcmMatiere(matiereId, utilisateurId);

        assertThat(resultat).isSameAs(dejaGenere);
        verify(agregateurContenuMatiereService, never()).agregerContenu(any());
    }

    @Test
    void soumettreTentative_calcule_le_score() {
        UUID matiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        QcmMatiere qcm = new QcmMatiere(matiereId, List.of(
                new QuestionQcm("Q1", "A", "B", "C", "D", 0, "exp"),
                new QuestionQcm("Q2", "A", "B", "C", "D", 1, "exp")
        ), StatutQcm.REUSSI);
        when(qcmMatiereRepository.findByMatiereId(matiereId)).thenReturn(Optional.of(qcm));
        when(tentativeQcmRepository.findByQcmIdAndUtilisateurId(qcm.getId(), utilisateurId)).thenReturn(Optional.empty());
        when(tentativeQcmRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TentativeQcm resultat = qcmMatiereService.soumettreTentative(matiereId, utilisateurId, List.of(0, 0));

        assertThat(resultat.getScore()).isEqualTo(1);
    }
}
