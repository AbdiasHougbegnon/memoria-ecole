package com.memoria.ecole.exercice;

import com.memoria.core.couloir.PasMembreDuCouloirException;
import com.memoria.ecole.matiere.AgregateurContenuMatiereService;
import com.memoria.ecole.matiere.MatiereService;
import com.memoria.ecole.notion.NiveauMaitrise;
import com.memoria.ecole.notion.Notion;
import com.memoria.ecole.notion.NotionService;
import com.memoria.ecole.qcm.StatutQcm;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExerciceSaisieLibreServiceTest {

    @Mock private ExerciceMatiereRepository exerciceMatiereRepository;
    @Mock private MatiereService matiereService;
    @Mock private NotionService notionService;
    @Mock private AgregateurContenuMatiereService agregateurContenuMatiereService;
    @Mock private GenerateurExerciceSaisieLibrePort generateurExercice;
    @Mock private TentativeExerciceSaisieLibreRepository tentativeRepository;

    private ExerciceSaisieLibreService service;

    @BeforeEach
    void setUp() {
        service = new ExerciceSaisieLibreService(
                exerciceMatiereRepository, matiereService, notionService, agregateurContenuMatiereService,
                generateurExercice, tentativeRepository
        );
    }

    @Test
    void obtenirOuGenererExercices_genere_a_partir_du_contenu_agrege() {
        UUID matiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        doNothing().when(matiereService).verifierMembreDuCouloir(matiereId, utilisateurId);
        when(exerciceMatiereRepository.findByMatiereId(matiereId)).thenReturn(Optional.empty());
        when(notionService.listerNotionsParMatiere(matiereId)).thenReturn(List.of());
        when(agregateurContenuMatiereService.agregerContenu(matiereId)).thenReturn("Contenu de plusieurs cours.");
        when(generateurExercice.genererExercices(any())).thenReturn(new ExercicesGeneres(List.of(
                new QuestionSaisieLibreExtraite("Explique les listes chainees.", "Doit mentionner noeud et pointeur")
        )));
        when(exerciceMatiereRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ExerciceMatiere resultat = service.obtenirOuGenererExercices(matiereId, utilisateurId);

        ArgumentCaptor<ExerciceMatiere> captor = ArgumentCaptor.forClass(ExerciceMatiere.class);
        verify(exerciceMatiereRepository).save(captor.capture());
        assertThat(captor.getValue().getQuestions()).hasSize(1);
        assertThat(captor.getValue().getStatut()).isEqualTo(StatutQcm.REUSSI);
        assertThat(resultat).isEqualTo(captor.getValue());
    }

    @Test
    void obtenirOuGenererExercices_inclut_les_notions_dans_le_contenu_envoye_au_generateur() {
        UUID matiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        Notion notion = new Notion(matiereId, "Pile", "Structure LIFO", 0);
        doNothing().when(matiereService).verifierMembreDuCouloir(matiereId, utilisateurId);
        when(exerciceMatiereRepository.findByMatiereId(matiereId)).thenReturn(Optional.empty());
        when(notionService.listerNotionsParMatiere(matiereId)).thenReturn(List.of(notion));
        when(agregateurContenuMatiereService.agregerContenu(matiereId)).thenReturn("");
        when(generateurExercice.genererExercices(any())).thenReturn(new ExercicesGeneres(List.of(
                new QuestionSaisieLibreExtraite("Explique la pile.", "Doit mentionner LIFO")
        )));
        when(exerciceMatiereRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.obtenirOuGenererExercices(matiereId, utilisateurId);

        ArgumentCaptor<String> contenuCapture = ArgumentCaptor.forClass(String.class);
        verify(generateurExercice).genererExercices(contenuCapture.capture());
        assertThat(contenuCapture.getValue()).contains("Pile : Structure LIFO");
    }

    @Test
    void obtenirOuGenererExercices_leve_une_exception_si_lutilisateur_nest_pas_membre() {
        UUID matiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        doThrow(new PasMembreDuCouloirException(UUID.randomUUID(), utilisateurId))
                .when(matiereService).verifierMembreDuCouloir(matiereId, utilisateurId);

        assertThatThrownBy(() -> service.obtenirOuGenererExercices(matiereId, utilisateurId))
                .isInstanceOf(PasMembreDuCouloirException.class);
        verify(exerciceMatiereRepository, never()).save(any());
    }

    @Test
    void obtenirOuGenererExercices_leve_une_exception_si_aucun_contenu_disponible() {
        UUID matiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        doNothing().when(matiereService).verifierMembreDuCouloir(matiereId, utilisateurId);
        when(exerciceMatiereRepository.findByMatiereId(matiereId)).thenReturn(Optional.empty());
        when(notionService.listerNotionsParMatiere(matiereId)).thenReturn(List.of());
        when(agregateurContenuMatiereService.agregerContenu(matiereId)).thenReturn("");

        assertThatThrownBy(() -> service.obtenirOuGenererExercices(matiereId, utilisateurId))
                .isInstanceOf(AucunContenuDisponiblePourExerciceException.class);
        verify(generateurExercice, never()).genererExercices(any());
    }

    @Test
    void soumettreReponses_evalue_chaque_reponse_et_les_sauvegarde() {
        UUID matiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        ExerciceMatiere exercice = new ExerciceMatiere(matiereId, List.of(
                new QuestionSaisieLibre("Explique les listes chainees.", "noeud, pointeur")
        ), StatutQcm.REUSSI);
        when(exerciceMatiereRepository.findByMatiereId(matiereId)).thenReturn(Optional.of(exercice));
        when(generateurExercice.evaluerReponse(eq("Explique les listes chainees."), eq("noeud, pointeur"), eq("Une liste ou chaque element pointe vers le suivant.")))
                .thenReturn(new EvaluationReponseLibre(NiveauMaitrise.MAITRISEE, "Bonne reponse."));
        when(tentativeRepository.findByExerciceMatiereIdAndUtilisateurId(exercice.getId(), utilisateurId)).thenReturn(Optional.empty());
        when(tentativeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TentativeExerciceSaisieLibre resultat = service.soumettreReponses(
                matiereId, utilisateurId, List.of("Une liste ou chaque element pointe vers le suivant.")
        );

        assertThat(resultat.getReponses()).hasSize(1);
        assertThat(resultat.getReponses().get(0).getNiveau()).isEqualTo(NiveauMaitrise.MAITRISEE);
        assertThat(resultat.getReponses().get(0).getRetour()).isEqualTo("Bonne reponse.");
    }

    @Test
    void soumettreReponses_ne_perd_pas_les_autres_reponses_si_une_evaluation_echoue() {
        UUID matiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        ExerciceMatiere exercice = new ExerciceMatiere(matiereId, List.of(
                new QuestionSaisieLibre("Question 1", "elements 1"),
                new QuestionSaisieLibre("Question 2", "elements 2")
        ), StatutQcm.REUSSI);
        when(exerciceMatiereRepository.findByMatiereId(matiereId)).thenReturn(Optional.of(exercice));
        when(generateurExercice.evaluerReponse(eq("Question 1"), any(), any()))
                .thenThrow(new RuntimeException("Azure OpenAI indisponible"));
        when(generateurExercice.evaluerReponse(eq("Question 2"), any(), any()))
                .thenReturn(new EvaluationReponseLibre(NiveauMaitrise.EN_COURS, "Partiellement correct."));
        when(tentativeRepository.findByExerciceMatiereIdAndUtilisateurId(exercice.getId(), utilisateurId)).thenReturn(Optional.empty());
        when(tentativeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TentativeExerciceSaisieLibre resultat = service.soumettreReponses(matiereId, utilisateurId, List.of("reponse 1", "reponse 2"));

        assertThat(resultat.getReponses()).hasSize(2);
        assertThat(resultat.getReponses().get(0).getNiveau()).isNull();
        assertThat(resultat.getReponses().get(1).getNiveau()).isEqualTo(NiveauMaitrise.EN_COURS);
    }
}
