package com.memoria.ecole.exercice;

import com.memoria.core.couloir.PasMembreDuCouloirException;
import com.memoria.core.document.ExtracteurDocumentPort;
import com.memoria.core.document.StatutDocument;
import com.memoria.core.document.StockageDocumentPort;
import com.memoria.ecole.matiere.MatiereService;
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
    @Mock private MatiereService matiereService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private TravailPapierService service;

    @BeforeEach
    void setUp() {
        service = new TravailPapierService(
                travailPapierRepository, stockageDocument, extracteurDocument, matiereService, eventPublisher
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
    void surTravailPapierTeleverse_marque_reussi_avec_le_texte_extrait() throws Exception {
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

            service.surTravailPapierTeleverse(new TravailPapierTeleverseEvent(travail.getId()));

            ArgumentCaptor<TravailPapierMatiere> captor = ArgumentCaptor.forClass(TravailPapierMatiere.class);
            verify(travailPapierRepository).save(captor.capture());
            assertThat(captor.getValue().getStatut()).isEqualTo(StatutDocument.REUSSI);
            assertThat(captor.getValue().getTexteExtrait()).isEqualTo("Exercice resolu a la main.");
        } finally {
            java.nio.file.Files.deleteIfExists(fichierTemp);
        }
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
