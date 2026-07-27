package com.memoria.ecole.document;

import com.memoria.core.couloir.CouloirService;
import com.memoria.core.couloir.PasProprietaireDuCouloirException;
import com.memoria.core.document.ExtracteurDocumentPort;
import com.memoria.core.document.ExtractionDocumentException;
import com.memoria.core.document.StatutDocument;
import com.memoria.core.document.StockageDocumentPort;
import com.memoria.core.document.TypeDocument;
import com.memoria.ecole.matiere.Matiere;
import com.memoria.ecole.matiere.MatiereNotFoundException;
import com.memoria.ecole.matiere.MatiereService;
import com.memoria.ecole.notion.NotionCandidate;
import com.memoria.ecole.notion.NotionCandidateRepository;

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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentMatiereServiceTest {

    @Mock
    private DocumentMatiereRepository documentMatiereRepository;

    @Mock
    private NotionCandidateRepository notionCandidateRepository;

    @Mock
    private StockageDocumentPort stockageDocument;

    @Mock
    private ExtracteurDocumentPort extracteurDocument;

    @Mock
    private GenerateurNotionsDepuisDocumentPort generateurNotions;

    @Mock
    private MatiereService matiereService;

    @Mock
    private CouloirService couloirService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private DocumentMatiereService documentMatiereService;

    @BeforeEach
    void setUp() {
        documentMatiereService = new DocumentMatiereService(
                documentMatiereRepository, notionCandidateRepository, stockageDocument, extracteurDocument,
                generateurNotions, matiereService, couloirService, eventPublisher
        );
    }

    @Test
    void televerser_sauvegarde_le_document_si_proprietaire_du_couloir() {
        UUID proprietaireId = UUID.randomUUID();
        UUID couloirId = UUID.randomUUID();
        UUID matiereId = UUID.randomUUID();
        when(matiereService.obtenirMatiere(matiereId)).thenReturn(new Matiere("Maths", couloirId, proprietaireId));
        when(stockageDocument.sauvegarder(eq(matiereId), anyString(), any())).thenReturn("/data/documents-matiere/x");
        when(documentMatiereRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentMatiere document = documentMatiereService.televerser(
                matiereId, "fiche.pdf", "application/pdf", new byte[]{1}, proprietaireId
        );

        assertThat(document.getMatiereId()).isEqualTo(matiereId);
        assertThat(document.getType()).isEqualTo(TypeDocument.PDF);
        assertThat(document.getStatut()).isEqualTo(StatutDocument.EN_ATTENTE);
    }

    @Test
    void televerser_leve_une_exception_si_pas_proprietaire_du_couloir() {
        UUID couloirId = UUID.randomUUID();
        UUID matiereId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        when(matiereService.obtenirMatiere(matiereId)).thenReturn(new Matiere("Maths", couloirId, UUID.randomUUID()));
        doThrow(new PasProprietaireDuCouloirException(couloirId, utilisateurId))
                .when(couloirService).verifierProprietaireDuCouloir(couloirId, utilisateurId);

        assertThatThrownBy(() -> documentMatiereService.televerser(
                matiereId, "fiche.pdf", "application/pdf", new byte[]{1}, utilisateurId
        )).isInstanceOf(PasProprietaireDuCouloirException.class);
        verify(stockageDocument, never()).sauvegarder(any(), any(), any());
    }

    @Test
    void televerser_leve_une_exception_si_la_matiere_est_introuvable() {
        UUID matiereId = UUID.randomUUID();
        when(matiereService.obtenirMatiere(matiereId)).thenThrow(new MatiereNotFoundException(matiereId));

        assertThatThrownBy(() -> documentMatiereService.televerser(
                matiereId, "fiche.pdf", "application/pdf", new byte[]{1}, UUID.randomUUID()
        )).isInstanceOf(MatiereNotFoundException.class);
        verify(stockageDocument, never()).sauvegarder(any(), any(), any());
    }

    @Test
    void televerser_publie_un_evenement_apres_sauvegarde() {
        UUID proprietaireId = UUID.randomUUID();
        UUID couloirId = UUID.randomUUID();
        UUID matiereId = UUID.randomUUID();
        when(matiereService.obtenirMatiere(matiereId)).thenReturn(new Matiere("Maths", couloirId, proprietaireId));
        when(stockageDocument.sauvegarder(eq(matiereId), anyString(), any())).thenReturn("/data/documents-matiere/x");
        when(documentMatiereRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentMatiere document = documentMatiereService.televerser(
                matiereId, "fiche.pdf", "application/pdf", new byte[]{1}, proprietaireId
        );

        verify(eventPublisher).publishEvent(new DocumentMatiereTeleverseEvent(document.getId()));
    }

    @Test
    void surDocumentTeleverse_marque_reussi_et_sauvegarde_les_candidats_generes() throws Exception {
        UUID matiereId = UUID.randomUUID();
        Path fichier = Files.createTempFile("document-matiere-test", ".bin");
        Files.write(fichier, new byte[]{1, 2, 3});
        DocumentMatiere document = new DocumentMatiere(matiereId, TypeDocument.PDF, "fiche.pdf", fichier.toString());
        when(documentMatiereRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(extracteurDocument.extraireTexte(any())).thenReturn("Les derivees mesurent un taux de variation.");
        when(generateurNotions.genererNotionsCandidates("Les derivees mesurent un taux de variation."))
                .thenReturn(List.of(new GenerateurNotionsDepuisDocumentPort.CandidatNotionGenere(
                        "Derivee", "Taux de variation instantane"
                )));

        documentMatiereService.surDocumentTeleverse(new DocumentMatiereTeleverseEvent(document.getId()));

        ArgumentCaptor<DocumentMatiere> captorDocument = ArgumentCaptor.forClass(DocumentMatiere.class);
        verify(documentMatiereRepository).save(captorDocument.capture());
        assertThat(captorDocument.getValue().getStatut()).isEqualTo(StatutDocument.REUSSI);
        assertThat(captorDocument.getValue().getTexteExtrait()).isEqualTo("Les derivees mesurent un taux de variation.");

        ArgumentCaptor<NotionCandidate> captorCandidate = ArgumentCaptor.forClass(NotionCandidate.class);
        verify(notionCandidateRepository).save(captorCandidate.capture());
        assertThat(captorCandidate.getValue().getDocumentMatiereId()).isEqualTo(document.getId());
        assertThat(captorCandidate.getValue().getMatiereId()).isEqualTo(matiereId);
        assertThat(captorCandidate.getValue().getTerme()).isEqualTo("Derivee");
        assertThat(captorCandidate.getValue().getDefinition()).isEqualTo("Taux de variation instantane");
    }

    @Test
    void surDocumentTeleverse_marque_echec_quand_lextraction_echoue_et_ne_genere_aucun_candidat() throws Exception {
        UUID matiereId = UUID.randomUUID();
        Path fichier = Files.createTempFile("document-matiere-test", ".bin");
        Files.write(fichier, new byte[]{1});
        DocumentMatiere document = new DocumentMatiere(matiereId, TypeDocument.PHOTO, "fiche.jpg", fichier.toString());
        when(documentMatiereRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(extracteurDocument.extraireTexte(any())).thenThrow(new ExtractionDocumentException("Azure indisponible"));

        documentMatiereService.surDocumentTeleverse(new DocumentMatiereTeleverseEvent(document.getId()));

        ArgumentCaptor<DocumentMatiere> captor = ArgumentCaptor.forClass(DocumentMatiere.class);
        verify(documentMatiereRepository).save(captor.capture());
        assertThat(captor.getValue().getStatut()).isEqualTo(StatutDocument.ECHEC);
        verify(generateurNotions, never()).genererNotionsCandidates(any());
        verify(notionCandidateRepository, never()).save(any());
    }

    @Test
    void surDocumentTeleverse_garde_le_document_reussi_meme_si_la_generation_de_candidats_echoue() throws Exception {
        UUID matiereId = UUID.randomUUID();
        Path fichier = Files.createTempFile("document-matiere-test", ".bin");
        Files.write(fichier, new byte[]{1});
        DocumentMatiere document = new DocumentMatiere(matiereId, TypeDocument.PDF, "fiche.pdf", fichier.toString());
        when(documentMatiereRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(extracteurDocument.extraireTexte(any())).thenReturn("Texte extrait");
        when(generateurNotions.genererNotionsCandidates(any()))
                .thenThrow(new GenerationNotionsDepuisDocumentException("Azure OpenAI indisponible"));

        documentMatiereService.surDocumentTeleverse(new DocumentMatiereTeleverseEvent(document.getId()));

        ArgumentCaptor<DocumentMatiere> captor = ArgumentCaptor.forClass(DocumentMatiere.class);
        verify(documentMatiereRepository).save(captor.capture());
        assertThat(captor.getValue().getStatut()).isEqualTo(StatutDocument.REUSSI);
        verify(notionCandidateRepository, never()).save(any());
    }

    @Test
    void surDocumentTeleverse_ne_fait_rien_si_le_document_est_introuvable() {
        when(documentMatiereRepository.findById(any())).thenReturn(Optional.empty());

        documentMatiereService.surDocumentTeleverse(new DocumentMatiereTeleverseEvent(UUID.randomUUID()));

        verify(documentMatiereRepository, never()).save(any());
        verify(generateurNotions, never()).genererNotionsCandidates(any());
    }

    @Test
    void listerDocuments_retourne_les_documents_de_la_matiere() {
        UUID matiereId = UUID.randomUUID();
        when(matiereService.obtenirMatiere(matiereId)).thenReturn(new Matiere("Maths", UUID.randomUUID(), UUID.randomUUID()));
        List<DocumentMatiere> documents = List.of(new DocumentMatiere(matiereId, TypeDocument.PHOTO, "a.jpg", "/x"));
        when(documentMatiereRepository.findByMatiereIdOrderByDateCreationAsc(matiereId)).thenReturn(documents);

        List<DocumentMatiere> resultat = documentMatiereService.listerDocuments(matiereId);

        assertThat(resultat).isEqualTo(documents);
    }

    @Test
    void listerDocuments_leve_une_exception_si_la_matiere_est_introuvable() {
        UUID matiereId = UUID.randomUUID();
        when(matiereService.obtenirMatiere(matiereId)).thenThrow(new MatiereNotFoundException(matiereId));

        assertThatThrownBy(() -> documentMatiereService.listerDocuments(matiereId))
                .isInstanceOf(MatiereNotFoundException.class);
    }
}
