package com.memoria.ecole.exercice;

import com.memoria.core.couloir.PasMembreDuCouloirException;
import com.memoria.core.document.ExtracteurDocumentPort;
import com.memoria.core.document.StatutDocument;
import com.memoria.core.document.StockageDocumentPort;
import com.memoria.ecole.matiere.MatiereService;
import com.memoria.ecole.notion.NiveauMaitrise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TravailPapierServiceTest {

    @Mock private TravailPapierMatiereRepository travailPapierRepository;
    @Mock private StockageDocumentPort stockageDocument;
    @Mock private ExtracteurDocumentPort extracteurDocument;
    @Mock private CorrecteurTravailPapierPort correcteurTravailPapier;
    @Mock private MatiereService matiereService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private TravailPapierService service;

    @BeforeEach
    void setUp() {
        service = new TravailPapierService(
                travailPapierRepository, stockageDocument, extracteurDocument, correcteurTravailPapier,
                matiereService, eventPublisher
        );
    }

    @Test
    void soumettre_sauvegarde_le_travail_et_publie_un_evenement() {
        UUID matiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        when(stockageDocument.sauvegarder(any(), any(), any())).thenReturn("chemin/photo.jpg");
        when(travailPapierRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TravailPapierMatiere resultat = service.soumettre(matiereId, "photo.jpg", "image/jpeg", new byte[]{1, 2, 3}, utilisateurId);

        assertThat(resultat.getMatiereId()).isEqualTo(matiereId);
        assertThat(resultat.getUtilisateurId()).isEqualTo(utilisateurId);
        assertThat(resultat.getStatut()).isEqualTo(StatutDocument.EN_ATTENTE);
        verify(eventPublisher).publishEvent(any(TravailPapierTeleverseEvent.class));
    }

    @Test
    void soumettre_leve_une_exception_si_lutilisateur_nest_pas_membre_du_couloir() {
        UUID matiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        doThrow(new PasMembreDuCouloirException(UUID.randomUUID(), utilisateurId))
                .when(matiereService).verifierMembreDuCouloir(matiereId, utilisateurId);

        assertThatThrownBy(() -> service.soumettre(matiereId, "photo.jpg", "image/jpeg", new byte[]{1}, utilisateurId))
                .isInstanceOf(PasMembreDuCouloirException.class);
        verify(travailPapierRepository, never()).save(any());
    }

    @Test
    void surTravailPapierTeleverse_marque_reussi_avec_le_texte_extrait_et_la_correction() throws Exception {
        UUID matiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        java.nio.file.Path fichierTemp = java.nio.file.Files.createTempFile("travail-papier-test", ".jpg");
        java.nio.file.Files.write(fichierTemp, new byte[]{1, 2, 3});
        try {
            TravailPapierMatiere travail = new TravailPapierMatiere(
                    matiereId, utilisateurId, com.memoria.core.document.TypeDocument.PHOTO, "photo.jpg", fichierTemp.toString()
            );
            when(travailPapierRepository.findById(travail.getId())).thenReturn(Optional.of(travail));
            when(extracteurDocument.extraireTexte(any())).thenReturn("Exercice resolu a la main.");
            when(correcteurTravailPapier.corriger("Exercice resolu a la main."))
                    .thenReturn(new CorrectionTravailPapier(NiveauMaitrise.EN_COURS, "Le raisonnement est correct mais la conclusion est erronee."));

            service.surTravailPapierTeleverse(new TravailPapierTeleverseEvent(travail.getId()));

            ArgumentCaptor<TravailPapierMatiere> captor = ArgumentCaptor.forClass(TravailPapierMatiere.class);
            verify(travailPapierRepository).save(captor.capture());
            assertThat(captor.getValue().getStatut()).isEqualTo(StatutDocument.REUSSI);
            assertThat(captor.getValue().getTexteExtrait()).isEqualTo("Exercice resolu a la main.");
            assertThat(captor.getValue().getCorrectionNiveau()).isEqualTo(NiveauMaitrise.EN_COURS);
            assertThat(captor.getValue().getCorrectionTexte()).isEqualTo("Le raisonnement est correct mais la conclusion est erronee.");
        } finally {
            java.nio.file.Files.deleteIfExists(fichierTemp);
        }
    }

    @Test
    void surTravailPapierTeleverse_garde_le_texte_extrait_meme_si_la_correction_echoue() throws Exception {
        UUID matiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        java.nio.file.Path fichierTemp = java.nio.file.Files.createTempFile("travail-papier-test", ".jpg");
        java.nio.file.Files.write(fichierTemp, new byte[]{1, 2, 3});
        try {
            TravailPapierMatiere travail = new TravailPapierMatiere(
                    matiereId, utilisateurId, com.memoria.core.document.TypeDocument.PHOTO, "photo.jpg", fichierTemp.toString()
            );
            when(travailPapierRepository.findById(travail.getId())).thenReturn(Optional.of(travail));
            when(extracteurDocument.extraireTexte(any())).thenReturn("Exercice resolu a la main.");
            when(correcteurTravailPapier.corriger(any())).thenThrow(new RuntimeException("Azure OpenAI indisponible"));

            service.surTravailPapierTeleverse(new TravailPapierTeleverseEvent(travail.getId()));

            ArgumentCaptor<TravailPapierMatiere> captor = ArgumentCaptor.forClass(TravailPapierMatiere.class);
            verify(travailPapierRepository).save(captor.capture());
            assertThat(captor.getValue().getStatut()).isEqualTo(StatutDocument.REUSSI);
            assertThat(captor.getValue().getTexteExtrait()).isEqualTo("Exercice resolu a la main.");
            assertThat(captor.getValue().getCorrectionNiveau()).isNull();
        } finally {
            java.nio.file.Files.deleteIfExists(fichierTemp);
        }
    }

    @Test
    void reessayerCorrection_corrige_un_travail_deja_extrait_sans_correction() {
        UUID matiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        TravailPapierMatiere travail = new TravailPapierMatiere(
                matiereId, utilisateurId, com.memoria.core.document.TypeDocument.PDF, "fiche.pdf", "chemin/fiche.pdf"
        );
        travail.marquerReussi("Contenu de la fiche.");
        when(travailPapierRepository.findById(travail.getId())).thenReturn(Optional.of(travail));
        when(correcteurTravailPapier.corriger("Contenu de la fiche."))
                .thenReturn(new CorrectionTravailPapier(NiveauMaitrise.MAITRISEE, "Tout est correct."));
        when(travailPapierRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TravailPapierMatiere resultat = service.reessayerCorrection(matiereId, travail.getId(), utilisateurId);

        assertThat(resultat.getCorrectionNiveau()).isEqualTo(NiveauMaitrise.MAITRISEE);
        assertThat(resultat.getCorrectionTexte()).isEqualTo("Tout est correct.");
    }

    @Test
    void reessayerCorrection_leve_une_exception_si_lutilisateur_nest_pas_le_proprietaire() {
        UUID matiereId = UUID.randomUUID();
        TravailPapierMatiere travail = new TravailPapierMatiere(
                matiereId, UUID.randomUUID(), com.memoria.core.document.TypeDocument.PDF, "fiche.pdf", "chemin/fiche.pdf"
        );
        travail.marquerReussi("Contenu de la fiche.");
        when(travailPapierRepository.findById(travail.getId())).thenReturn(Optional.of(travail));

        assertThatThrownBy(() -> service.reessayerCorrection(matiereId, travail.getId(), UUID.randomUUID()))
                .isInstanceOf(AccesTravailPapierRefuseException.class);
        verify(correcteurTravailPapier, never()).corriger(any());
    }

    @Test
    void reessayerCorrection_leve_une_exception_si_aucun_texte_extrait() {
        UUID matiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        TravailPapierMatiere travail = new TravailPapierMatiere(
                matiereId, utilisateurId, com.memoria.core.document.TypeDocument.PDF, "fiche.pdf", "chemin/fiche.pdf"
        );
        when(travailPapierRepository.findById(travail.getId())).thenReturn(Optional.of(travail));

        assertThatThrownBy(() -> service.reessayerCorrection(matiereId, travail.getId(), utilisateurId))
                .isInstanceOf(TexteExtraitIndisponibleException.class);
        verify(correcteurTravailPapier, never()).corriger(any());
    }

    @Test
    void listerMesTravaux_delegue_au_repository() {
        UUID matiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        TravailPapierMatiere travail = new TravailPapierMatiere(
                matiereId, utilisateurId, com.memoria.core.document.TypeDocument.PHOTO, "photo.jpg", "chemin"
        );
        when(travailPapierRepository.findByMatiereIdAndUtilisateurIdOrderByDateCreationDesc(matiereId, utilisateurId))
                .thenReturn(List.of(travail));

        List<TravailPapierMatiere> resultat = service.listerMesTravaux(matiereId, utilisateurId);

        assertThat(resultat).containsExactly(travail);
    }
}
