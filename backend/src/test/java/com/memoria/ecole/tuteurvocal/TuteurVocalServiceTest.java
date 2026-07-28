package com.memoria.ecole.tuteurvocal;

import com.memoria.core.couloir.CouloirService;
import com.memoria.core.couloir.PasMembreDuCouloirException;
import com.memoria.core.transcription.ResultatTranscription;
import com.memoria.core.transcription.TranscripteurPort;
import com.memoria.ecole.exercice.TravailPapierMatiereRepository;
import com.memoria.ecole.matiere.AgregateurContenuMatiereService;
import com.memoria.ecole.matiere.Matiere;
import com.memoria.ecole.matiere.MatiereService;
import com.memoria.ecole.notion.NiveauMaitrise;
import com.memoria.ecole.notion.Notion;
import com.memoria.ecole.notion.NotionService;
import com.memoria.ecole.seance.Seance;
import com.memoria.ecole.seance.SeanceService;

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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TuteurVocalServiceTest {

    @Mock
    private SeanceTutoratRepository seanceTutoratRepository;

    @Mock
    private TourDialogueTutoratRepository tourDialogueTutoratRepository;

    @Mock
    private SeanceService seanceService;

    @Mock
    private NotionService notionService;

    @Mock
    private MatiereService matiereService;

    @Mock
    private CouloirService couloirService;

    @Mock
    private TranscripteurPort transcripteur;

    @Mock
    private SynthetiseurVocalPort synthetiseurVocal;

    @Mock
    private GenerateurTourTuteurPort generateurTourTuteur;

    @Mock
    private AgregateurContenuMatiereService agregateurContenuMatiereService;

    @Mock
    private TravailPapierMatiereRepository travailPapierMatiereRepository;

    private TuteurVocalService tuteurVocalService;

    @BeforeEach
    void setUp() {
        tuteurVocalService = new TuteurVocalService(
                seanceTutoratRepository, tourDialogueTutoratRepository, seanceService, notionService, matiereService,
                couloirService, transcripteur, synthetiseurVocal, generateurTourTuteur, agregateurContenuMatiereService,
                travailPapierMatiereRepository
        );
    }

    @Test
    void demarrerTutorat_choisit_la_premiere_notion_non_maitrisee_et_genere_le_tour_douverture() {
        UUID couloirId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        Seance seance = new Seance("Cours 1", UUID.randomUUID(), couloirId);
        Notion notion1 = new Notion(UUID.randomUUID(), "Derivees", "def1", 0);
        Notion notion2 = new Notion(UUID.randomUUID(), "Integrales", "def2", 1);

        when(seanceService.obtenirSeance(seance.getId())).thenReturn(seance);
        when(couloirService.estMembre(couloirId, utilisateurId)).thenReturn(true);
        when(seanceTutoratRepository.findBySeanceIdAndUtilisateurIdAndStatut(seance.getId(), utilisateurId, StatutSeanceTutorat.EN_COURS))
                .thenReturn(Optional.empty());
        when(seanceService.listerNotionsDeSeance(seance.getId())).thenReturn(List.of(notion1, notion2));
        when(notionService.obtenirNiveauMaitrise(notion1.getId(), utilisateurId)).thenReturn(NiveauMaitrise.NON_ABORDEE);
        when(seanceTutoratRepository.save(any(SeanceTutorat.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(notionService.obtenirNotion(notion1.getId())).thenReturn(notion1);
        when(generateurTourTuteur.genererTour(any())).thenReturn(
                new GenerateurTourTuteurPort.TourTuteurGenere("Bonjour, parlons des derivees.", NiveauMaitrise.NON_ABORDEE, false)
        );
        when(tourDialogueTutoratRepository.save(any(TourDialogueTutorat.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResultatTour resultat = tuteurVocalService.demarrerTutorat(seance.getId(), utilisateurId, ModeTutorat.EXPLICATION);

        assertThat(resultat.notionCouranteId()).isEqualTo(notion1.getId());
        assertThat(resultat.texteTuteur()).isEqualTo("Bonjour, parlons des derivees.");
        assertThat(resultat.seanceTerminee()).isFalse();
    }

    @Test
    void demarrerTutorat_leve_une_exception_si_pas_membre_du_couloir() {
        UUID couloirId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        Seance seance = new Seance("Cours 1", UUID.randomUUID(), couloirId);
        when(seanceService.obtenirSeance(seance.getId())).thenReturn(seance);
        when(couloirService.estMembre(couloirId, utilisateurId)).thenReturn(false);

        assertThatThrownBy(() -> tuteurVocalService.demarrerTutorat(seance.getId(), utilisateurId, ModeTutorat.EXPLICATION))
                .isInstanceOf(PasMembreDuCouloirException.class);
    }

    @Test
    void demarrerTutorat_reprend_la_seance_existante_si_deja_en_cours() {
        UUID couloirId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        Seance seance = new Seance("Cours 1", UUID.randomUUID(), couloirId);
        UUID notionId = UUID.randomUUID();
        SeanceTutorat existante = new SeanceTutorat(seance.getId(), utilisateurId, notionId, ModeTutorat.EXPLICATION);
        TourDialogueTutorat dernierTour = new TourDialogueTutorat(existante.getId(), notionId, Locuteur.TUTEUR, "Reprise");

        when(seanceService.obtenirSeance(seance.getId())).thenReturn(seance);
        when(couloirService.estMembre(couloirId, utilisateurId)).thenReturn(true);
        when(seanceTutoratRepository.findBySeanceIdAndUtilisateurIdAndStatut(seance.getId(), utilisateurId, StatutSeanceTutorat.EN_COURS))
                .thenReturn(Optional.of(existante));
        when(tourDialogueTutoratRepository.findBySeanceTutoratIdOrderByDateCreationAsc(existante.getId()))
                .thenReturn(List.of(dernierTour));
        when(notionService.obtenirNiveauMaitrise(notionId, utilisateurId)).thenReturn(NiveauMaitrise.EN_COURS);

        ResultatTour resultat = tuteurVocalService.demarrerTutorat(seance.getId(), utilisateurId, ModeTutorat.EXPLICATION);

        assertThat(resultat.seanceTutoratId()).isEqualTo(existante.getId());
        assertThat(resultat.texteTuteur()).isEqualTo("Reprise");
        org.mockito.Mockito.verify(seanceTutoratRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void demarrerTutorat_termine_immediatement_si_toutes_les_notions_deja_maitrisees() {
        UUID couloirId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        Seance seance = new Seance("Cours 1", UUID.randomUUID(), couloirId);
        Notion notion1 = new Notion(UUID.randomUUID(), "Derivees", "def1", 0);

        when(seanceService.obtenirSeance(seance.getId())).thenReturn(seance);
        when(couloirService.estMembre(couloirId, utilisateurId)).thenReturn(true);
        when(seanceTutoratRepository.findBySeanceIdAndUtilisateurIdAndStatut(seance.getId(), utilisateurId, StatutSeanceTutorat.EN_COURS))
                .thenReturn(Optional.empty());
        when(seanceService.listerNotionsDeSeance(seance.getId())).thenReturn(List.of(notion1));
        when(notionService.obtenirNiveauMaitrise(notion1.getId(), utilisateurId)).thenReturn(NiveauMaitrise.MAITRISEE);
        when(seanceTutoratRepository.save(any(SeanceTutorat.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResultatTour resultat = tuteurVocalService.demarrerTutorat(seance.getId(), utilisateurId, ModeTutorat.EXPLICATION);

        assertThat(resultat.seanceTerminee()).isTrue();
        assertThat(resultat.notionCouranteId()).isNull();
    }

    @Test
    void soumettreReponse_reste_sur_la_meme_notion_si_pas_encore_maitrisee() {
        UUID utilisateurId = UUID.randomUUID();
        UUID notionId = UUID.randomUUID();
        SeanceTutorat seanceTutorat = new SeanceTutorat(UUID.randomUUID(), utilisateurId, notionId, ModeTutorat.EXPLICATION);
        Notion notion = new Notion(UUID.randomUUID(), "Derivees", "def1", 0);

        when(seanceTutoratRepository.findById(seanceTutorat.getId())).thenReturn(Optional.of(seanceTutorat));
        when(notionService.obtenirNotion(notionId)).thenReturn(notion);
        when(transcripteur.transcrire(any())).thenReturn(new ResultatTranscription("une reponse partielle", List.of()));
        when(tourDialogueTutoratRepository.findBySeanceTutoratIdAndNotionIdOrderByDateCreationAsc(seanceTutorat.getId(), notionId))
                .thenReturn(List.of());
        when(tourDialogueTutoratRepository.save(any(TourDialogueTutorat.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(generateurTourTuteur.genererTour(any())).thenReturn(
                new GenerateurTourTuteurPort.TourTuteurGenere("Pas tout a fait, essaie encore.", NiveauMaitrise.EN_COURS, false)
        );

        ResultatTour resultat = tuteurVocalService.soumettreReponse(seanceTutorat.getId(), new byte[]{1, 2, 3}, utilisateurId);

        assertThat(resultat.notionCouranteId()).isEqualTo(notionId);
        assertThat(resultat.seanceTerminee()).isFalse();
        org.mockito.Mockito.verify(notionService).mettreAJourMaitrise(notionId, utilisateurId, NiveauMaitrise.EN_COURS);
    }

    @Test
    void soumettreReponse_avance_a_la_notion_suivante_si_maitrisee_et_quil_en_reste() {
        UUID utilisateurId = UUID.randomUUID();
        UUID seanceId = UUID.randomUUID();
        Notion notion1 = new Notion(UUID.randomUUID(), "Derivees", "def1", 0);
        Notion notion2 = new Notion(UUID.randomUUID(), "Integrales", "def2", 1);
        SeanceTutorat seanceTutorat = new SeanceTutorat(seanceId, utilisateurId, notion1.getId(), ModeTutorat.EXPLICATION);

        when(seanceTutoratRepository.findById(seanceTutorat.getId())).thenReturn(Optional.of(seanceTutorat));
        when(notionService.obtenirNotion(notion1.getId())).thenReturn(notion1);
        when(notionService.obtenirNotion(notion2.getId())).thenReturn(notion2);
        when(transcripteur.transcrire(any())).thenReturn(new ResultatTranscription("bonne reponse", List.of()));
        when(tourDialogueTutoratRepository.findBySeanceTutoratIdAndNotionIdOrderByDateCreationAsc(seanceTutorat.getId(), notion1.getId()))
                .thenReturn(List.of());
        when(tourDialogueTutoratRepository.save(any(TourDialogueTutorat.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(generateurTourTuteur.genererTour(any()))
                .thenReturn(new GenerateurTourTuteurPort.TourTuteurGenere("Bravo, c'est maitrise.", NiveauMaitrise.MAITRISEE, true))
                .thenReturn(new GenerateurTourTuteurPort.TourTuteurGenere("Passons aux integrales.", NiveauMaitrise.NON_ABORDEE, false));
        when(seanceService.listerNotionsDeSeance(seanceId)).thenReturn(List.of(notion1, notion2));
        when(notionService.obtenirNiveauMaitrise(notion1.getId(), utilisateurId)).thenReturn(NiveauMaitrise.MAITRISEE);
        when(notionService.obtenirNiveauMaitrise(notion2.getId(), utilisateurId)).thenReturn(NiveauMaitrise.NON_ABORDEE);
        when(seanceTutoratRepository.save(any(SeanceTutorat.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResultatTour resultat = tuteurVocalService.soumettreReponse(seanceTutorat.getId(), new byte[]{1, 2, 3}, utilisateurId);

        assertThat(resultat.notionCouranteId()).isEqualTo(notion2.getId());
        assertThat(resultat.seanceTerminee()).isFalse();
        assertThat(resultat.texteTuteur()).contains("Bravo, c'est maitrise.").contains("Passons aux integrales.");
    }

    @Test
    void soumettreReponse_termine_la_seance_si_derniere_notion_maitrisee() {
        UUID utilisateurId = UUID.randomUUID();
        UUID seanceId = UUID.randomUUID();
        Notion notion1 = new Notion(UUID.randomUUID(), "Derivees", "def1", 0);
        SeanceTutorat seanceTutorat = new SeanceTutorat(seanceId, utilisateurId, notion1.getId(), ModeTutorat.EXPLICATION);

        when(seanceTutoratRepository.findById(seanceTutorat.getId())).thenReturn(Optional.of(seanceTutorat));
        when(notionService.obtenirNotion(notion1.getId())).thenReturn(notion1);
        when(transcripteur.transcrire(any())).thenReturn(new ResultatTranscription("bonne reponse", List.of()));
        when(tourDialogueTutoratRepository.findBySeanceTutoratIdAndNotionIdOrderByDateCreationAsc(seanceTutorat.getId(), notion1.getId()))
                .thenReturn(List.of());
        when(tourDialogueTutoratRepository.save(any(TourDialogueTutorat.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(generateurTourTuteur.genererTour(any()))
                .thenReturn(new GenerateurTourTuteurPort.TourTuteurGenere("Bravo, c'est maitrise.", NiveauMaitrise.MAITRISEE, true));
        when(seanceService.listerNotionsDeSeance(seanceId)).thenReturn(List.of(notion1));
        when(notionService.obtenirNiveauMaitrise(notion1.getId(), utilisateurId)).thenReturn(NiveauMaitrise.MAITRISEE);
        when(seanceTutoratRepository.save(any(SeanceTutorat.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResultatTour resultat = tuteurVocalService.soumettreReponse(seanceTutorat.getId(), new byte[]{1, 2, 3}, utilisateurId);

        assertThat(resultat.seanceTerminee()).isTrue();
        assertThat(seanceTutorat.getStatut()).isEqualTo(StatutSeanceTutorat.TERMINEE);
    }

    @Test
    void soumettreReponse_leve_une_exception_si_lutilisateur_nest_pas_le_proprietaire() {
        UUID proprietaireId = UUID.randomUUID();
        SeanceTutorat seanceTutorat = new SeanceTutorat(UUID.randomUUID(), proprietaireId, UUID.randomUUID(), ModeTutorat.EXPLICATION);
        when(seanceTutoratRepository.findById(seanceTutorat.getId())).thenReturn(Optional.of(seanceTutorat));

        assertThatThrownBy(() -> tuteurVocalService.soumettreReponse(seanceTutorat.getId(), new byte[0], UUID.randomUUID()))
                .isInstanceOf(AccesTutoratRefuseException.class);
    }

    @Test
    void soumettreReponse_leve_une_exception_si_la_seance_nest_plus_active() {
        UUID utilisateurId = UUID.randomUUID();
        SeanceTutorat seanceTutorat = new SeanceTutorat(UUID.randomUUID(), utilisateurId, UUID.randomUUID(), ModeTutorat.EXPLICATION);
        seanceTutorat.terminer();
        when(seanceTutoratRepository.findById(seanceTutorat.getId())).thenReturn(Optional.of(seanceTutorat));

        assertThatThrownBy(() -> tuteurVocalService.soumettreReponse(seanceTutorat.getId(), new byte[0], utilisateurId))
                .isInstanceOf(SeanceTutoratNonActiveException.class);
    }

    @Test
    void arreterTutorat_termine_la_seance() {
        UUID utilisateurId = UUID.randomUUID();
        SeanceTutorat seanceTutorat = new SeanceTutorat(UUID.randomUUID(), utilisateurId, UUID.randomUUID(), ModeTutorat.EXPLICATION);
        when(seanceTutoratRepository.findById(seanceTutorat.getId())).thenReturn(Optional.of(seanceTutorat));
        when(seanceTutoratRepository.save(any(SeanceTutorat.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SeanceTutorat resultat = tuteurVocalService.arreterTutorat(seanceTutorat.getId(), utilisateurId);

        assertThat(resultat.getStatut()).isEqualTo(StatutSeanceTutorat.TERMINEE);
        assertThat(resultat.getDateFin()).isNotNull();
    }

    @Test
    void demarrerTutorat_en_mode_libre_ne_resout_aucune_notion_et_ne_genere_pas_de_premier_tour() {
        UUID couloirId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        Seance seance = new Seance("Cours 1", UUID.randomUUID(), couloirId);

        when(seanceService.obtenirSeance(seance.getId())).thenReturn(seance);
        when(couloirService.estMembre(couloirId, utilisateurId)).thenReturn(true);
        when(seanceTutoratRepository.findBySeanceIdAndUtilisateurIdAndStatut(seance.getId(), utilisateurId, StatutSeanceTutorat.EN_COURS))
                .thenReturn(Optional.empty());
        when(seanceTutoratRepository.save(any(SeanceTutorat.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResultatTour resultat = tuteurVocalService.demarrerTutorat(seance.getId(), utilisateurId, ModeTutorat.LIBRE);

        assertThat(resultat.tourId()).isNull();
        assertThat(resultat.notionCouranteId()).isNull();
        assertThat(resultat.niveauMaitrise()).isNull();
        assertThat(resultat.seanceTerminee()).isFalse();
        org.mockito.Mockito.verify(seanceService, org.mockito.Mockito.never()).listerNotionsDeSeance(any());
        org.mockito.Mockito.verifyNoInteractions(generateurTourTuteur);
        org.mockito.Mockito.verify(tourDialogueTutoratRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void soumettreReponse_en_mode_libre_utilise_lhistorique_complet_et_najamais_evalue_de_maitrise() {
        UUID utilisateurId = UUID.randomUUID();
        UUID seanceId = UUID.randomUUID();
        UUID matiereId = UUID.randomUUID();
        Seance seance = new Seance("Cours 1", matiereId, UUID.randomUUID());
        Matiere matiere = new Matiere("Mathematiques", UUID.randomUUID(), UUID.randomUUID());
        SeanceTutorat seanceTutorat = new SeanceTutorat(seanceId, utilisateurId, null, ModeTutorat.LIBRE);
        TourDialogueTutorat tourPrecedent = new TourDialogueTutorat(seanceTutorat.getId(), null, Locuteur.ETUDIANT, "C'est quoi une derivee ?");

        when(seanceTutoratRepository.findById(seanceTutorat.getId())).thenReturn(Optional.of(seanceTutorat));
        when(transcripteur.transcrire(any())).thenReturn(new ResultatTranscription("et une integrale ?", List.of()));
        when(tourDialogueTutoratRepository.findBySeanceTutoratIdOrderByDateCreationAsc(seanceTutorat.getId()))
                .thenReturn(List.of(tourPrecedent));
        when(tourDialogueTutoratRepository.save(any(TourDialogueTutorat.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(seanceService.obtenirSeance(seanceId)).thenReturn(seance);
        when(matiereService.obtenirMatiere(matiereId)).thenReturn(matiere);
        when(agregateurContenuMatiereService.agregerContenu(matiere.getId())).thenReturn("");
        when(generateurTourTuteur.genererTour(any())).thenReturn(
                new GenerateurTourTuteurPort.TourTuteurGenere("Une integrale, c'est...", null, false)
        );

        ResultatTour resultat = tuteurVocalService.soumettreReponse(seanceTutorat.getId(), new byte[]{1, 2, 3}, utilisateurId);

        assertThat(resultat.notionCouranteId()).isNull();
        assertThat(resultat.niveauMaitrise()).isNull();
        assertThat(resultat.seanceTerminee()).isFalse();
        assertThat(resultat.texteTuteur()).isEqualTo("Une integrale, c'est...");
        org.mockito.Mockito.verify(tourDialogueTutoratRepository, org.mockito.Mockito.never())
                .findBySeanceTutoratIdAndNotionIdOrderByDateCreationAsc(any(), any());
        org.mockito.Mockito.verify(notionService, org.mockito.Mockito.never())
                .mettreAJourMaitrise(any(), any(), any());

        var contexteCapture = org.mockito.ArgumentCaptor.forClass(GenerateurTourTuteurPort.ContexteTour.class);
        org.mockito.Mockito.verify(generateurTourTuteur).genererTour(contexteCapture.capture());
        assertThat(contexteCapture.getValue().notionTerme()).isEqualTo("Mathematiques");
        assertThat(contexteCapture.getValue().mode()).isEqualTo(ModeTutorat.LIBRE);
        assertThat(contexteCapture.getValue().historique()).hasSize(1);
    }

    // Phase 18 -> 19 : les notions validees par l'enseignant (issues des
    // fiches uploadees) nourrissent le mode LIBRE -- voir
    // TuteurVocalService.construireContexteMatiere.
    @Test
    void soumettreReponse_en_mode_libre_injecte_les_notions_validees_de_la_matiere() {
        UUID utilisateurId = UUID.randomUUID();
        UUID seanceId = UUID.randomUUID();
        UUID matiereId = UUID.randomUUID();
        Seance seance = new Seance("Cours 1", matiereId, UUID.randomUUID());
        Matiere matiere = new Matiere("Algorithmique", UUID.randomUUID(), UUID.randomUUID());
        Notion notion = new Notion(matiere.getId(), "Pile", "Structure LIFO", 0);
        SeanceTutorat seanceTutorat = new SeanceTutorat(seanceId, utilisateurId, null, ModeTutorat.LIBRE);

        when(seanceTutoratRepository.findById(seanceTutorat.getId())).thenReturn(Optional.of(seanceTutorat));
        when(transcripteur.transcrire(any())).thenReturn(new ResultatTranscription("C'est quoi une pile ?", List.of()));
        when(tourDialogueTutoratRepository.findBySeanceTutoratIdOrderByDateCreationAsc(seanceTutorat.getId()))
                .thenReturn(List.of());
        when(tourDialogueTutoratRepository.save(any(TourDialogueTutorat.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(seanceService.obtenirSeance(seanceId)).thenReturn(seance);
        when(matiereService.obtenirMatiere(matiereId)).thenReturn(matiere);
        when(notionService.listerNotionsParMatiere(matiere.getId())).thenReturn(List.of(notion));
        when(agregateurContenuMatiereService.agregerContenu(matiere.getId())).thenReturn("");
        when(generateurTourTuteur.genererTour(any())).thenReturn(
                new GenerateurTourTuteurPort.TourTuteurGenere("Une pile fonctionne en LIFO...", null, false)
        );

        tuteurVocalService.soumettreReponse(seanceTutorat.getId(), new byte[]{1, 2, 3}, utilisateurId);

        var contexteCapture = org.mockito.ArgumentCaptor.forClass(GenerateurTourTuteurPort.ContexteTour.class);
        org.mockito.Mockito.verify(generateurTourTuteur).genererTour(contexteCapture.capture());
        assertThat(contexteCapture.getValue().notionDefinition()).contains("Pile").contains("Structure LIFO");
    }

    // Phase 22c : elargit deliberement au contenu agrege de la matiere
    // (resumes de cours + documents), pas seulement les notions validees --
    // voir AgregateurContenuMatiereService et docs/phases/phase-22-tutorat-progressif.md.
    @Test
    void soumettreReponse_en_mode_libre_injecte_le_contenu_agrege_de_la_matiere() {
        UUID utilisateurId = UUID.randomUUID();
        UUID seanceId = UUID.randomUUID();
        UUID matiereId = UUID.randomUUID();
        Seance seance = new Seance("Cours 1", matiereId, UUID.randomUUID());
        Matiere matiere = new Matiere("Algorithmique", UUID.randomUUID(), UUID.randomUUID());
        SeanceTutorat seanceTutorat = new SeanceTutorat(seanceId, utilisateurId, null, ModeTutorat.LIBRE);

        when(seanceTutoratRepository.findById(seanceTutorat.getId())).thenReturn(Optional.of(seanceTutorat));
        when(transcripteur.transcrire(any())).thenReturn(new ResultatTranscription("Recapitule le dernier cours.", List.of()));
        when(tourDialogueTutoratRepository.findBySeanceTutoratIdOrderByDateCreationAsc(seanceTutorat.getId()))
                .thenReturn(List.of());
        when(tourDialogueTutoratRepository.save(any(TourDialogueTutorat.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(seanceService.obtenirSeance(seanceId)).thenReturn(seance);
        when(matiereService.obtenirMatiere(matiereId)).thenReturn(matiere);
        when(notionService.listerNotionsParMatiere(matiere.getId())).thenReturn(List.of());
        when(agregateurContenuMatiereService.agregerContenu(matiere.getId()))
                .thenReturn("Le dernier cours portait sur les files (FIFO).");
        when(generateurTourTuteur.genererTour(any())).thenReturn(
                new GenerateurTourTuteurPort.TourTuteurGenere("On a vu les files...", null, false)
        );

        tuteurVocalService.soumettreReponse(seanceTutorat.getId(), new byte[]{1, 2, 3}, utilisateurId);

        var contexteCapture = org.mockito.ArgumentCaptor.forClass(GenerateurTourTuteurPort.ContexteTour.class);
        org.mockito.Mockito.verify(generateurTourTuteur).genererTour(contexteCapture.capture());
        assertThat(contexteCapture.getValue().notionDefinition()).contains("Le dernier cours portait sur les files (FIFO).");
    }

    // Phase 22e : le texte extrait d'un travail papier soumis par CET
    // etudiant nourrit ses propres conversations avec le tuteur -- voir
    // TravailPapierService et docs/phases/phase-22-tutorat-progressif.md.
    @Test
    void soumettreReponse_en_mode_libre_injecte_les_travaux_papier_de_lutilisateur() {
        UUID utilisateurId = UUID.randomUUID();
        UUID seanceId = UUID.randomUUID();
        UUID matiereId = UUID.randomUUID();
        Seance seance = new Seance("Cours 1", matiereId, UUID.randomUUID());
        Matiere matiere = new Matiere("Algorithmique", UUID.randomUUID(), UUID.randomUUID());
        SeanceTutorat seanceTutorat = new SeanceTutorat(seanceId, utilisateurId, null, ModeTutorat.LIBRE);
        com.memoria.ecole.exercice.TravailPapierMatiere travail = new com.memoria.ecole.exercice.TravailPapierMatiere(
                matiere.getId(), utilisateurId, com.memoria.core.document.TypeDocument.PHOTO, "exercice.jpg", "chemin/exercice.jpg"
        );
        travail.marquerReussi("Exercice resolu a la main sur les piles et les files.");

        when(seanceTutoratRepository.findById(seanceTutorat.getId())).thenReturn(Optional.of(seanceTutorat));
        when(transcripteur.transcrire(any())).thenReturn(new ResultatTranscription("Peux-tu regarder mon exercice ?", List.of()));
        when(tourDialogueTutoratRepository.findBySeanceTutoratIdOrderByDateCreationAsc(seanceTutorat.getId()))
                .thenReturn(List.of());
        when(tourDialogueTutoratRepository.save(any(TourDialogueTutorat.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(seanceService.obtenirSeance(seanceId)).thenReturn(seance);
        when(matiereService.obtenirMatiere(matiereId)).thenReturn(matiere);
        when(notionService.listerNotionsParMatiere(matiere.getId())).thenReturn(List.of());
        when(agregateurContenuMatiereService.agregerContenu(matiere.getId())).thenReturn("");
        when(travailPapierMatiereRepository.findByMatiereIdAndUtilisateurIdOrderByDateCreationDesc(matiere.getId(), utilisateurId))
                .thenReturn(List.of(travail));
        when(generateurTourTuteur.genererTour(any())).thenReturn(
                new GenerateurTourTuteurPort.TourTuteurGenere("Regardons ca ensemble...", null, false)
        );

        tuteurVocalService.soumettreReponse(seanceTutorat.getId(), new byte[]{1, 2, 3}, utilisateurId);

        var contexteCapture = org.mockito.ArgumentCaptor.forClass(GenerateurTourTuteurPort.ContexteTour.class);
        org.mockito.Mockito.verify(generateurTourTuteur).genererTour(contexteCapture.capture());
        assertThat(contexteCapture.getValue().notionDefinition()).contains("Exercice resolu a la main sur les piles et les files.");
    }

    @Test
    void obtenirAudioDuTour_resynthetise_le_texte_du_tour() {
        TourDialogueTutorat tour = new TourDialogueTutorat(UUID.randomUUID(), UUID.randomUUID(), Locuteur.TUTEUR, "Bonjour");
        byte[] audioAttendu = new byte[]{1, 2, 3};
        when(tourDialogueTutoratRepository.findById(tour.getId())).thenReturn(Optional.of(tour));
        when(synthetiseurVocal.synthetiser(anyString())).thenReturn(audioAttendu);

        byte[] resultat = tuteurVocalService.obtenirAudioDuTour(tour.getId());

        assertThat(resultat).isEqualTo(audioAttendu);
    }
}
