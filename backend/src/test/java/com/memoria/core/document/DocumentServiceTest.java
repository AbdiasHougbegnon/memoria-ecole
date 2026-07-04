package com.memoria.core.document;

import com.memoria.core.session.Session;
import com.memoria.core.session.SessionNotFoundException;
import com.memoria.core.session.SessionService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private StockageDocumentPort stockageDocument;

    @Mock
    private ExtracteurDocumentPort extracteurDocument;

    @Mock
    private SessionService sessionService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        documentService = new DocumentService(
                documentRepository, stockageDocument, extracteurDocument, sessionService, eventPublisher
        );
    }

    @Test
    void televerser_detecte_le_type_pdf_via_le_content_type() {
        UUID sessionId = UUID.randomUUID();
        when(sessionService.obtenirSession(sessionId)).thenReturn(new Session("Cours"));
        when(stockageDocument.sauvegarder(eq(sessionId), anyString(), any())).thenReturn("/data/documents/x");
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Document document = documentService.televerser(sessionId, "notes.bin", "application/pdf", new byte[]{1});

        assertThat(document.getType()).isEqualTo(TypeDocument.PDF);
    }

    @Test
    void televerser_detecte_le_type_pdf_via_extension_si_le_content_type_est_absent() {
        UUID sessionId = UUID.randomUUID();
        when(sessionService.obtenirSession(sessionId)).thenReturn(new Session("Cours"));
        when(stockageDocument.sauvegarder(eq(sessionId), anyString(), any())).thenReturn("/data/documents/x");
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Document document = documentService.televerser(sessionId, "slides.pdf", null, new byte[]{1});

        assertThat(document.getType()).isEqualTo(TypeDocument.PDF);
    }

    @Test
    void televerser_retombe_sur_photo_par_defaut() {
        UUID sessionId = UUID.randomUUID();
        when(sessionService.obtenirSession(sessionId)).thenReturn(new Session("Cours"));
        when(stockageDocument.sauvegarder(eq(sessionId), anyString(), any())).thenReturn("/data/documents/x");
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Document document = documentService.televerser(sessionId, "tableau.jpg", "image/jpeg", new byte[]{1});

        assertThat(document.getType()).isEqualTo(TypeDocument.PHOTO);
    }

    @Test
    void televerser_publie_un_evenement_apres_sauvegarde() {
        UUID sessionId = UUID.randomUUID();
        when(sessionService.obtenirSession(sessionId)).thenReturn(new Session("Cours"));
        when(stockageDocument.sauvegarder(eq(sessionId), anyString(), any())).thenReturn("/data/documents/x");
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Document document = documentService.televerser(sessionId, "photo.jpg", "image/jpeg", new byte[]{1});

        verify(eventPublisher).publishEvent(new DocumentTeleverseEvent(document.getId()));
    }

    @Test
    void televerser_leve_une_exception_si_la_session_est_introuvable() {
        UUID idInconnu = UUID.randomUUID();
        when(sessionService.obtenirSession(idInconnu)).thenThrow(new SessionNotFoundException(idInconnu));

        assertThatThrownBy(() -> documentService.televerser(idInconnu, "a.jpg", "image/jpeg", new byte[]{1}))
                .isInstanceOf(SessionNotFoundException.class);
        verify(stockageDocument, never()).sauvegarder(any(), any(), any());
    }

    @Test
    void surDocumentTeleverse_marque_reussi_avec_le_texte_extrait() throws Exception {
        UUID sessionId = UUID.randomUUID();
        Path fichier = Files.createTempFile("document-test", ".bin");
        Files.write(fichier, new byte[]{1, 2, 3});
        Document document = new Document(sessionId, TypeDocument.PHOTO, "a.jpg", fichier.toString());
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(extracteurDocument.extraireTexte(any())).thenReturn("Texte extrait");

        documentService.surDocumentTeleverse(new DocumentTeleverseEvent(document.getId()));

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatut()).isEqualTo(StatutDocument.REUSSI);
        assertThat(captor.getValue().getTexteExtrait()).isEqualTo("Texte extrait");
    }

    @Test
    void surDocumentTeleverse_marque_echec_quand_l_extraction_echoue() throws Exception {
        UUID sessionId = UUID.randomUUID();
        Path fichier = Files.createTempFile("document-test", ".bin");
        Files.write(fichier, new byte[]{1});
        Document document = new Document(sessionId, TypeDocument.PHOTO, "a.jpg", fichier.toString());
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(extracteurDocument.extraireTexte(any())).thenThrow(new ExtractionDocumentException("Azure indisponible"));

        documentService.surDocumentTeleverse(new DocumentTeleverseEvent(document.getId()));

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatut()).isEqualTo(StatutDocument.ECHEC);
        assertThat(captor.getValue().getTexteExtrait()).isNull();
    }

    @Test
    void surDocumentTeleverse_ne_fait_rien_si_le_document_est_introuvable() {
        when(documentRepository.findById(any())).thenReturn(Optional.empty());

        documentService.surDocumentTeleverse(new DocumentTeleverseEvent(UUID.randomUUID()));

        verify(documentRepository, never()).save(any());
    }

    @Test
    void obtenirDocuments_retourne_les_documents_de_la_session() {
        UUID sessionId = UUID.randomUUID();
        when(sessionService.obtenirSession(sessionId)).thenReturn(new Session("Cours"));
        List<Document> documents = List.of(new Document(sessionId, TypeDocument.PHOTO, "a.jpg", "/x"));
        when(documentRepository.findBySessionIdOrderByDateCreationAsc(sessionId)).thenReturn(documents);

        List<Document> resultat = documentService.obtenirDocuments(sessionId);

        assertThat(resultat).isEqualTo(documents);
    }
}
