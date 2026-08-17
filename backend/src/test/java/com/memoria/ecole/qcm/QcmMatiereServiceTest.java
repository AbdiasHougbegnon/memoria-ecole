package com.memoria.ecole.qcm;

import com.memoria.core.couloir.PasMembreDuCouloirException;
import com.memoria.ecole.matiere.AgregateurContenuMatiereService;
import com.memoria.ecole.matiere.MatiereService;
import com.memoria.ecole.notion.Notion;
import com.memoria.ecole.notion.NotionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    @Mock private QcmMatiereNotionRepository qcmMatiereNotionRepository;
    @Mock private MatiereService matiereService;
    @Mock private NotionService notionService;
    @Mock private AgregateurContenuMatiereService agregateurContenuMatiereService;
    @Mock private GenerateurQcmPort generateurQcm;
    @Mock private TentativeQcmRepository tentativeQcmRepository;

    private QcmMatiereService qcmMatiereService;

    @BeforeEach
    void setUp() {
        qcmMatiereService = new QcmMatiereService(
                qcmMatiereRepository, qcmMatiereNotionRepository, matiereService, notionService,
                agregateurContenuMatiereService, generateurQcm, tentativeQcmRepository
        );
    }

    @Test
    void obtenirOuGenererQcmMatiere_genere_a_partir_du_contenu_agrege() {
        UUID matiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        Set<UUID> notionIds = Set.of(UUID.randomUUID());
        doNothing().when(matiereService).verifierMembreDuCouloir(matiereId, utilisateurId);
        when(qcmMatiereRepository.findByMatiereId(matiereId)).thenReturn(Optional.empty());
        when(notionService.listerNotionsParMatiere(matiereId)).thenReturn(List.of());
        when(agregateurContenuMatiereService.agregerContenu(matiereId))
                .thenReturn("Synthese de plusieurs cours sur les structures de donnees.");
        when(generateurQcm.genererQcm(any())).thenReturn(new QcmGenere(List.of(
                new QuestionExtraite("Qu'est-ce qu'une pile ?", List.of("LIFO", "FIFO", "Arbre", "Graphe"), 0, "Explication")
        )));
        when(qcmMatiereRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        QcmMatiere resultat = qcmMatiereService.obtenirOuGenererQcmMatiere(matiereId, notionIds, utilisateurId);

        ArgumentCaptor<QcmMatiere> captor = ArgumentCaptor.forClass(QcmMatiere.class);
        verify(qcmMatiereRepository).save(captor.capture());
        assertThat(captor.getValue().getMatiereId()).isEqualTo(matiereId);
        assertThat(captor.getValue().getQuestions()).hasSize(1);
        assertThat(captor.getValue().getStatut()).isEqualTo(StatutQcm.REUSSI);
        assertThat(resultat).isEqualTo(captor.getValue());
        verify(qcmMatiereNotionRepository).save(any(QcmMatiereNotion.class));
    }

    @Test
    void obtenirOuGenererQcmMatiere_inclut_les_notions_selectionnees_dans_le_contenu_envoye_au_generateur() {
        UUID matiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        Notion notion = new Notion(matiereId, "Pile", "Structure LIFO", 0);
        doNothing().when(matiereService).verifierMembreDuCouloir(matiereId, utilisateurId);
        when(qcmMatiereRepository.findByMatiereId(matiereId)).thenReturn(Optional.empty());
        when(notionService.listerNotionsParMatiere(matiereId)).thenReturn(List.of(notion));
        when(agregateurContenuMatiereService.agregerContenu(matiereId)).thenReturn("");
        when(generateurQcm.genererQcm(any())).thenReturn(new QcmGenere(List.of(
                new QuestionExtraite("Qu'est-ce qu'une pile ?", List.of("LIFO", "FIFO", "Arbre", "Graphe"), 0, "Explication")
        )));
        when(qcmMatiereRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        qcmMatiereService.obtenirOuGenererQcmMatiere(matiereId, Set.of(notion.getId()), utilisateurId);

        ArgumentCaptor<String> contenuCapture = ArgumentCaptor.forClass(String.class);
        verify(generateurQcm).genererQcm(contenuCapture.capture());
        assertThat(contenuCapture.getValue()).contains("Pile : Structure LIFO");
    }

    // Une notion existant dans la matiere mais absente de la selection
    // demandee ne doit pas apparaitre dans le contenu envoye au generateur.
    @Test
    void obtenirOuGenererQcmMatiere_exclut_les_notions_non_selectionnees() {
        UUID matiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        Notion notionSelectionnee = new Notion(matiereId, "Pile", "Structure LIFO", 0);
        Notion notionExclue = new Notion(matiereId, "File", "Structure FIFO", 1);
        doNothing().when(matiereService).verifierMembreDuCouloir(matiereId, utilisateurId);
        when(qcmMatiereRepository.findByMatiereId(matiereId)).thenReturn(Optional.empty());
        when(notionService.listerNotionsParMatiere(matiereId)).thenReturn(List.of(notionSelectionnee, notionExclue));
        when(agregateurContenuMatiereService.agregerContenu(matiereId)).thenReturn("");
        when(generateurQcm.genererQcm(any())).thenReturn(new QcmGenere(List.of(
                new QuestionExtraite("Qu'est-ce qu'une pile ?", List.of("LIFO", "FIFO", "Arbre", "Graphe"), 0, "Explication")
        )));
        when(qcmMatiereRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        qcmMatiereService.obtenirOuGenererQcmMatiere(matiereId, Set.of(notionSelectionnee.getId()), utilisateurId);

        ArgumentCaptor<String> contenuCapture = ArgumentCaptor.forClass(String.class);
        verify(generateurQcm).genererQcm(contenuCapture.capture());
        assertThat(contenuCapture.getValue()).contains("Pile : Structure LIFO").doesNotContain("File : Structure FIFO");
    }

    @Test
    void obtenirOuGenererQcmMatiere_leve_une_exception_si_lutilisateur_nest_pas_membre_du_couloir() {
        UUID matiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        doThrow(new PasMembreDuCouloirException(UUID.randomUUID(), utilisateurId))
                .when(matiereService).verifierMembreDuCouloir(matiereId, utilisateurId);

        assertThatThrownBy(() -> qcmMatiereService.obtenirOuGenererQcmMatiere(matiereId, Set.of(UUID.randomUUID()), utilisateurId))
                .isInstanceOf(PasMembreDuCouloirException.class);
        verify(qcmMatiereRepository, never()).save(any());
    }

    @Test
    void obtenirOuGenererQcmMatiere_leve_une_exception_si_aucun_contenu_disponible() {
        UUID matiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        doNothing().when(matiereService).verifierMembreDuCouloir(matiereId, utilisateurId);
        when(qcmMatiereRepository.findByMatiereId(matiereId)).thenReturn(Optional.empty());
        when(notionService.listerNotionsParMatiere(matiereId)).thenReturn(List.of());
        when(agregateurContenuMatiereService.agregerContenu(matiereId)).thenReturn("");

        assertThatThrownBy(() -> qcmMatiereService.obtenirOuGenererQcmMatiere(matiereId, Set.of(UUID.randomUUID()), utilisateurId))
                .isInstanceOf(AucunContenuMatiereDisponibleException.class);
        verify(generateurQcm, never()).genererQcm(any());
    }

    @Test
    void obtenirOuGenererQcmMatiere_renvoie_le_qcm_deja_en_cache_si_meme_selection() {
        UUID matiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        UUID notionId = UUID.randomUUID();
        QcmMatiere dejaGenere = new QcmMatiere(matiereId, List.of(), StatutQcm.REUSSI);
        doNothing().when(matiereService).verifierMembreDuCouloir(matiereId, utilisateurId);
        when(qcmMatiereRepository.findByMatiereId(matiereId)).thenReturn(Optional.of(dejaGenere));
        when(qcmMatiereNotionRepository.findByQcmMatiereId(dejaGenere.getId()))
                .thenReturn(List.of(new QcmMatiereNotion(dejaGenere.getId(), notionId)));

        QcmMatiere resultat = qcmMatiereService.obtenirOuGenererQcmMatiere(matiereId, Set.of(notionId), utilisateurId);

        assertThat(resultat).isSameAs(dejaGenere);
        verify(agregateurContenuMatiereService, never()).agregerContenu(any());
        verify(qcmMatiereRepository, never()).deleteById(any());
    }

    // Coeur du changement : une selection differente de celle du QCM deja
    // persiste doit le remplacer (regeneration), pas le reutiliser tel quel.
    @Test
    void obtenirOuGenererQcmMatiere_regenere_si_la_selection_de_notions_differe() {
        UUID matiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        UUID ancienneNotionId = UUID.randomUUID();
        Notion nouvelleNotion = new Notion(matiereId, "File", "Structure FIFO", 0);
        UUID nouvelleNotionId = nouvelleNotion.getId();
        QcmMatiere dejaGenere = new QcmMatiere(matiereId, List.of(), StatutQcm.REUSSI);
        doNothing().when(matiereService).verifierMembreDuCouloir(matiereId, utilisateurId);
        when(qcmMatiereRepository.findByMatiereId(matiereId)).thenReturn(Optional.of(dejaGenere));
        when(qcmMatiereNotionRepository.findByQcmMatiereId(dejaGenere.getId()))
                .thenReturn(List.of(new QcmMatiereNotion(dejaGenere.getId(), ancienneNotionId)));
        when(notionService.listerNotionsParMatiere(matiereId)).thenReturn(List.of(nouvelleNotion));
        when(agregateurContenuMatiereService.agregerContenu(matiereId)).thenReturn("");
        when(generateurQcm.genererQcm(any())).thenReturn(new QcmGenere(List.of(
                new QuestionExtraite("Qu'est-ce qu'une file ?", List.of("LIFO", "FIFO", "Arbre", "Graphe"), 1, "Explication")
        )));
        when(qcmMatiereRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        QcmMatiere resultat = qcmMatiereService.obtenirOuGenererQcmMatiere(matiereId, Set.of(nouvelleNotionId), utilisateurId);

        verify(qcmMatiereNotionRepository).deleteByQcmMatiereId(dejaGenere.getId());
        verify(qcmMatiereRepository).deleteById(dejaGenere.getId());
        assertThat(resultat).isNotSameAs(dejaGenere);
        assertThat(resultat.getQuestions()).hasSize(1);
    }

    // Un ECHEC precedent (Azure OpenAI indisponible au moment de l'appel) ne
    // doit jamais etre servi comme cache -- sinon un simple hoquet reseau
    // bloquerait definitivement toute nouvelle tentative sur la meme selection.
    @Test
    void obtenirOuGenererQcmMatiere_retente_la_generation_si_le_cache_est_en_echec() {
        UUID matiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        UUID notionId = UUID.randomUUID();
        Notion notion = new Notion(matiereId, "Pile", "Structure LIFO", 0);
        QcmMatiere echecPrecedent = new QcmMatiere(matiereId, List.of(), StatutQcm.ECHEC);
        doNothing().when(matiereService).verifierMembreDuCouloir(matiereId, utilisateurId);
        when(qcmMatiereRepository.findByMatiereId(matiereId)).thenReturn(Optional.of(echecPrecedent));
        when(notionService.listerNotionsParMatiere(matiereId)).thenReturn(List.of(notion));
        when(agregateurContenuMatiereService.agregerContenu(matiereId)).thenReturn("");
        when(generateurQcm.genererQcm(any())).thenReturn(new QcmGenere(List.of(
                new QuestionExtraite("Qu'est-ce qu'une pile ?", List.of("LIFO", "FIFO", "Arbre", "Graphe"), 0, "Explication")
        )));
        when(qcmMatiereRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        QcmMatiere resultat = qcmMatiereService.obtenirOuGenererQcmMatiere(matiereId, Set.of(notion.getId()), utilisateurId);

        verify(qcmMatiereRepository).deleteById(echecPrecedent.getId());
        assertThat(resultat.getStatut()).isEqualTo(StatutQcm.REUSSI);
        assertThat(resultat.getQuestions()).hasSize(1);
        verify(qcmMatiereNotionRepository, never()).findByQcmMatiereId(any());
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
