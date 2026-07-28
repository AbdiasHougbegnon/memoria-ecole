package com.memoria.ecole.exercice;

import com.memoria.core.couloir.PasMembreDuCouloirException;
import com.memoria.core.document.ExtracteurDocumentPort;
import com.memoria.core.document.StatutDocument;
import com.memoria.core.document.StockageDocumentPort;
import com.memoria.core.document.TypeDocument;
import com.memoria.ecole.matiere.MatiereService;
import com.memoria.ecole.notion.NiveauMaitrise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TravailPapierServiceTest {

    @Mock private TravailPapierMatiereRepository travailPapierRepository;
    @Mock private ExercicePapierRepository exercicePapierRepository;
    @Mock private StockageDocumentPort stockageDocument;
    @Mock private ExtracteurDocumentPort extracteurDocument;
    @Mock private CorrecteurTravailPapierPort correcteurTravailPapier;
    @Mock private VerificateurComprehensionPort verificateurComprehension;
    @Mock private MatiereService matiereService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private TravailPapierService service;

    @BeforeEach
    void setUp() {
        service = new TravailPapierService(
                travailPapierRepository, exercicePapierRepository, stockageDocument, extracteurDocument,
                correcteurTravailPapier, verificateurComprehension, matiereService, eventPublisher
        );
    }

    @Test
    void soumettre_sauvegarde_le_travail_avec_les_deux_fichiers_et_publie_un_evenement() {
        UUID matiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        when(stockageDocument.sauvegarder(any(), any(), any())).thenReturn("chemin/fichier.jpg");
        when(travailPapierRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TravailPapierMatiere resultat = service.soumettre(
                matiereId,
                "enonce.jpg", "image/jpeg", new byte[]{1, 2, 3},
                "reponse.jpg", "image/jpeg", new byte[]{4, 5, 6},
                utilisateurId
        );

        assertThat(resultat.getMatiereId()).isEqualTo(matiereId);
        assertThat(resultat.getUtilisateurId()).isEqualTo(utilisateurId);
        assertThat(resultat.getNomFichierEnonce()).isEqualTo("enonce.jpg");
        assertThat(resultat.getNomFichierReponse()).isEqualTo("reponse.jpg");
        assertThat(resultat.getStatut()).isEqualTo(StatutDocument.EN_ATTENTE);
        verify(eventPublisher).publishEvent(any(TravailPapierTeleverseEvent.class));
    }

    @Test
    void soumettre_leve_une_exception_si_lutilisateur_nest_pas_membre_du_couloir() {
        UUID matiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        doThrow(new PasMembreDuCouloirException(UUID.randomUUID(), utilisateurId))
                .when(matiereService).verifierMembreDuCouloir(matiereId, utilisateurId);

        assertThatThrownBy(() -> service.soumettre(
                matiereId, "enonce.jpg", "image/jpeg", new byte[]{1}, "reponse.jpg", "image/jpeg", new byte[]{2}, utilisateurId
        )).isInstanceOf(PasMembreDuCouloirException.class);
        verify(travailPapierRepository, never()).save(any());
    }

    private TravailPapierMatiere creerTravailAvecFichiers(UUID matiereId, UUID utilisateurId, Path fichierEnonce, Path fichierReponse) {
        return new TravailPapierMatiere(
                matiereId, utilisateurId,
                TypeDocument.PHOTO, "enonce.jpg", fichierEnonce.toString(),
                TypeDocument.PHOTO, "reponse.jpg", fichierReponse.toString()
        );
    }

    @Test
    void surTravailPapierTeleverse_extrait_les_deux_textes_et_cree_les_exercices_corriges() throws Exception {
        UUID matiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        Path fichierEnonce = Files.createTempFile("travail-papier-enonce", ".jpg");
        Path fichierReponse = Files.createTempFile("travail-papier-reponse", ".jpg");
        Files.write(fichierEnonce, new byte[]{1});
        Files.write(fichierReponse, new byte[]{2});
        try {
            TravailPapierMatiere travail = creerTravailAvecFichiers(matiereId, utilisateurId, fichierEnonce, fichierReponse);
            when(travailPapierRepository.findById(travail.getId())).thenReturn(Optional.of(travail));
            when(extracteurDocument.extraireTexte(eq(new byte[]{1}))).thenReturn("Resoudre 2x+4=10");
            when(extracteurDocument.extraireTexte(eq(new byte[]{2}))).thenReturn("x=3");
            when(correcteurTravailPapier.corriger("Resoudre 2x+4=10", "x=3")).thenReturn(List.of(
                    new ExerciceCorrige(
                            "Resoudre 2x+4=10", "x=3", NiveauMaitrise.MAITRISEE, "Bonne reponse.",
                            List.of(new PointCorrection("Resolution", "Correcte", "Rien a signaler"))
                    )
            ));

            service.surTravailPapierTeleverse(new TravailPapierTeleverseEvent(travail.getId()));

            ArgumentCaptor<TravailPapierMatiere> captor = ArgumentCaptor.forClass(TravailPapierMatiere.class);
            verify(travailPapierRepository).save(captor.capture());
            assertThat(captor.getValue().getStatut()).isEqualTo(StatutDocument.REUSSI);
            assertThat(captor.getValue().getTexteExtraitEnonce()).isEqualTo("Resoudre 2x+4=10");
            assertThat(captor.getValue().getTexteExtraitReponse()).isEqualTo("x=3");

            verify(exercicePapierRepository).deleteByTravailPapierId(travail.getId());
            ArgumentCaptor<ExercicePapier> exerciceCaptor = ArgumentCaptor.forClass(ExercicePapier.class);
            verify(exercicePapierRepository).save(exerciceCaptor.capture());
            assertThat(exerciceCaptor.getValue().getEnonce()).isEqualTo("Resoudre 2x+4=10");
            assertThat(exerciceCaptor.getValue().getReponseEtudiant()).isEqualTo("x=3");
            assertThat(exerciceCaptor.getValue().getCorrectionNiveau()).isEqualTo(NiveauMaitrise.MAITRISEE);
        } finally {
            Files.deleteIfExists(fichierEnonce);
            Files.deleteIfExists(fichierReponse);
        }
    }

    @Test
    void surTravailPapierTeleverse_garde_les_textes_extraits_meme_si_la_correction_echoue() throws Exception {
        UUID matiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        Path fichierEnonce = Files.createTempFile("travail-papier-enonce", ".jpg");
        Path fichierReponse = Files.createTempFile("travail-papier-reponse", ".jpg");
        Files.write(fichierEnonce, new byte[]{1});
        Files.write(fichierReponse, new byte[]{2});
        try {
            TravailPapierMatiere travail = creerTravailAvecFichiers(matiereId, utilisateurId, fichierEnonce, fichierReponse);
            when(travailPapierRepository.findById(travail.getId())).thenReturn(Optional.of(travail));
            when(extracteurDocument.extraireTexte(any())).thenReturn("texte");
            when(correcteurTravailPapier.corriger(any(), any())).thenThrow(new RuntimeException("Azure OpenAI indisponible"));

            service.surTravailPapierTeleverse(new TravailPapierTeleverseEvent(travail.getId()));

            ArgumentCaptor<TravailPapierMatiere> captor = ArgumentCaptor.forClass(TravailPapierMatiere.class);
            verify(travailPapierRepository).save(captor.capture());
            assertThat(captor.getValue().getStatut()).isEqualTo(StatutDocument.REUSSI);
            assertThat(captor.getValue().getTexteExtraitEnonce()).isEqualTo("texte");
            verify(exercicePapierRepository, never()).save(any());
        } finally {
            Files.deleteIfExists(fichierEnonce);
            Files.deleteIfExists(fichierReponse);
        }
    }

    @Test
    void reessayerCorrection_corrige_un_travail_deja_extrait_sans_exercices() {
        UUID matiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        TravailPapierMatiere travail = new TravailPapierMatiere(
                matiereId, utilisateurId, TypeDocument.PDF, "enonce.pdf", "chemin/enonce.pdf", TypeDocument.PDF, "reponse.pdf", "chemin/reponse.pdf"
        );
        travail.marquerReussi("Enonce complet.", "Reponse complete.");
        when(travailPapierRepository.findById(travail.getId())).thenReturn(Optional.of(travail));
        when(correcteurTravailPapier.corriger("Enonce complet.", "Reponse complete.")).thenReturn(List.of(
                new ExerciceCorrige("Enonce complet.", "Reponse complete.", NiveauMaitrise.MAITRISEE, "Tout est correct.", List.of())
        ));
        when(travailPapierRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TravailPapierMatiere resultat = service.reessayerCorrection(matiereId, travail.getId(), utilisateurId);

        assertThat(resultat.getStatut()).isEqualTo(StatutDocument.REUSSI);
        verify(exercicePapierRepository).deleteByTravailPapierId(travail.getId());
        verify(exercicePapierRepository).save(any());
    }

    @Test
    void reessayerCorrection_leve_une_exception_si_lutilisateur_nest_pas_le_proprietaire() {
        UUID matiereId = UUID.randomUUID();
        TravailPapierMatiere travail = new TravailPapierMatiere(
                matiereId, UUID.randomUUID(), TypeDocument.PDF, "enonce.pdf", "chemin/enonce.pdf", TypeDocument.PDF, "reponse.pdf", "chemin/reponse.pdf"
        );
        travail.marquerReussi("Enonce.", "Reponse.");
        when(travailPapierRepository.findById(travail.getId())).thenReturn(Optional.of(travail));

        assertThatThrownBy(() -> service.reessayerCorrection(matiereId, travail.getId(), UUID.randomUUID()))
                .isInstanceOf(AccesTravailPapierRefuseException.class);
        verify(correcteurTravailPapier, never()).corriger(any(), any());
    }

    @Test
    void reessayerCorrection_leve_une_exception_si_aucun_texte_extrait() {
        UUID matiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        TravailPapierMatiere travail = new TravailPapierMatiere(
                matiereId, utilisateurId, TypeDocument.PDF, "enonce.pdf", "chemin/enonce.pdf", TypeDocument.PDF, "reponse.pdf", "chemin/reponse.pdf"
        );
        when(travailPapierRepository.findById(travail.getId())).thenReturn(Optional.of(travail));

        assertThatThrownBy(() -> service.reessayerCorrection(matiereId, travail.getId(), utilisateurId))
                .isInstanceOf(TexteExtraitIndisponibleException.class);
        verify(correcteurTravailPapier, never()).corriger(any(), any());
    }

    @Test
    void listerMesTravaux_delegue_au_repository() {
        UUID matiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        TravailPapierMatiere travail = new TravailPapierMatiere(
                matiereId, utilisateurId, TypeDocument.PHOTO, "enonce.jpg", "chemin1", TypeDocument.PHOTO, "reponse.jpg", "chemin2"
        );
        when(travailPapierRepository.findByMatiereIdAndUtilisateurIdOrderByDateCreationDesc(matiereId, utilisateurId))
                .thenReturn(List.of(travail));

        List<TravailPapierMatiere> resultat = service.listerMesTravaux(matiereId, utilisateurId);

        assertThat(resultat).containsExactly(travail);
    }

    @Test
    void listerExercices_delegue_au_repository() {
        UUID travailId = UUID.randomUUID();
        ExercicePapier exercice = new ExercicePapier(travailId, 0, "Enonce", "Reponse", NiveauMaitrise.MAITRISEE, "Synthese", List.of());
        when(exercicePapierRepository.findByTravailPapierIdOrderByOrdreAsc(travailId)).thenReturn(List.of(exercice));

        List<ExercicePapier> resultat = service.listerExercices(travailId);

        assertThat(resultat).containsExactly(exercice);
    }

    @Test
    void genererQuestionVerification_appelle_le_port_et_enregistre_la_question() {
        UUID matiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        TravailPapierMatiere travail = new TravailPapierMatiere(
                matiereId, utilisateurId, TypeDocument.PHOTO, "enonce.jpg", "chemin1", TypeDocument.PHOTO, "reponse.jpg", "chemin2"
        );
        UUID exerciceId = UUID.randomUUID();
        ExercicePapier exercice = new ExercicePapier(
                travail.getId(), 0, "Resoudre 2x+4=10", "x=3", NiveauMaitrise.MAITRISEE, "Bonne reponse.", List.of()
        );
        when(travailPapierRepository.findById(travail.getId())).thenReturn(Optional.of(travail));
        when(exercicePapierRepository.findById(exerciceId)).thenReturn(Optional.of(exercice));
        when(exercicePapierRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        List<ChoixVerification> choix = List.of(new ChoixVerification("x=3", true), new ChoixVerification("x=5", false));
        when(verificateurComprehension.genererQuestion(any(), any(), any()))
                .thenReturn(new QuestionVerificationGeneree("Pourquoi x=3 ?", choix));

        ExercicePapier resultat = service.genererQuestionVerification(matiereId, travail.getId(), exerciceId, utilisateurId);

        assertThat(resultat.getQuestionVerificationEnonce()).isEqualTo("Pourquoi x=3 ?");
        assertThat(resultat.getChoixVerification()).isEqualTo(choix);
        assertThat(resultat.getStatutVerification()).isEqualTo(StatutVerification.NON_VERIFIE);
    }

    @Test
    void soumettreReponseChoix_valide_quand_les_cases_cochees_correspondent_exactement_aux_choix_corrects() {
        UUID matiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        TravailPapierMatiere travail = new TravailPapierMatiere(
                matiereId, utilisateurId, TypeDocument.PHOTO, "enonce.jpg", "chemin1", TypeDocument.PHOTO, "reponse.jpg", "chemin2"
        );
        UUID exerciceId = UUID.randomUUID();
        ExercicePapier exercice = new ExercicePapier(
                travail.getId(), 0, "Enonce", "Reponse", NiveauMaitrise.MAITRISEE, "Synthese", List.of()
        );
        exercice.enregistrerQuestionVerification("Question ?", List.of(
                new ChoixVerification("Bon choix", true), new ChoixVerification("Mauvais choix", false)
        ));
        when(travailPapierRepository.findById(travail.getId())).thenReturn(Optional.of(travail));
        when(exercicePapierRepository.findById(exerciceId)).thenReturn(Optional.of(exercice));
        when(exercicePapierRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ExercicePapier resultat = service.soumettreReponseChoix(matiereId, travail.getId(), exerciceId, utilisateurId, List.of(0));

        assertThat(resultat.getStatutVerification()).isEqualTo(StatutVerification.VALIDE);
    }

    @Test
    void soumettreReponseChoix_marque_pas_clair_si_un_choix_incorrect_est_coche() {
        UUID matiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        TravailPapierMatiere travail = new TravailPapierMatiere(
                matiereId, utilisateurId, TypeDocument.PHOTO, "enonce.jpg", "chemin1", TypeDocument.PHOTO, "reponse.jpg", "chemin2"
        );
        UUID exerciceId = UUID.randomUUID();
        ExercicePapier exercice = new ExercicePapier(
                travail.getId(), 0, "Enonce", "Reponse", NiveauMaitrise.MAITRISEE, "Synthese", List.of()
        );
        exercice.enregistrerQuestionVerification("Question ?", List.of(
                new ChoixVerification("Bon choix", true), new ChoixVerification("Mauvais choix", false)
        ));
        when(travailPapierRepository.findById(travail.getId())).thenReturn(Optional.of(travail));
        when(exercicePapierRepository.findById(exerciceId)).thenReturn(Optional.of(exercice));
        when(exercicePapierRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ExercicePapier resultat = service.soumettreReponseChoix(matiereId, travail.getId(), exerciceId, utilisateurId, List.of(0, 1));

        assertThat(resultat.getStatutVerification()).isEqualTo(StatutVerification.PAS_CLAIR);
    }

    @Test
    void soumettreReponseLibre_valide_si_le_niveau_evalue_est_maitrise() {
        UUID matiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        TravailPapierMatiere travail = new TravailPapierMatiere(
                matiereId, utilisateurId, TypeDocument.PHOTO, "enonce.jpg", "chemin1", TypeDocument.PHOTO, "reponse.jpg", "chemin2"
        );
        UUID exerciceId = UUID.randomUUID();
        ExercicePapier exercice = new ExercicePapier(
                travail.getId(), 0, "Enonce", "Reponse", NiveauMaitrise.MAITRISEE, "Synthese", List.of()
        );
        exercice.enregistrerQuestionVerification("Question ?", List.of());
        when(travailPapierRepository.findById(travail.getId())).thenReturn(Optional.of(travail));
        when(exercicePapierRepository.findById(exerciceId)).thenReturn(Optional.of(exercice));
        when(exercicePapierRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(verificateurComprehension.evaluerReponseLibre(any(), any())).thenReturn(NiveauMaitrise.MAITRISEE);

        ExercicePapier resultat = service.soumettreReponseLibre(matiereId, travail.getId(), exerciceId, utilisateurId, "Ma reponse");

        assertThat(resultat.getStatutVerification()).isEqualTo(StatutVerification.VALIDE);
    }

    @Test
    void genererQuestionVerification_leve_une_exception_si_lutilisateur_nest_pas_le_proprietaire() {
        UUID matiereId = UUID.randomUUID();
        TravailPapierMatiere travail = new TravailPapierMatiere(
                matiereId, UUID.randomUUID(), TypeDocument.PHOTO, "enonce.jpg", "chemin1", TypeDocument.PHOTO, "reponse.jpg", "chemin2"
        );
        when(travailPapierRepository.findById(travail.getId())).thenReturn(Optional.of(travail));

        assertThatThrownBy(() -> service.genererQuestionVerification(matiereId, travail.getId(), UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(AccesTravailPapierRefuseException.class);
    }
}
