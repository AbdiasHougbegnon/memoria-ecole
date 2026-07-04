package com.memoria.core.document;

import com.memoria.core.session.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;
    private final StockageDocumentPort stockageDocument;
    private final ExtracteurDocumentPort extracteurDocument;
    private final SessionService sessionService;
    private final ApplicationEventPublisher eventPublisher;

    public DocumentService(
            DocumentRepository documentRepository,
            StockageDocumentPort stockageDocument,
            ExtracteurDocumentPort extracteurDocument,
            SessionService sessionService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.documentRepository = documentRepository;
        this.stockageDocument = stockageDocument;
        this.extracteurDocument = extracteurDocument;
        this.sessionService = sessionService;
        this.eventPublisher = eventPublisher;
    }

    public Document televerser(UUID sessionId, String nomFichierOriginal, String typeContenu, byte[] contenu) {
        // Contrairement a l'audio, un document peut etre depose que la
        // session soit en cours ou deja terminee : ce n'est pas un flux
        // temps reel, juste une piece jointe.
        sessionService.obtenirSession(sessionId);

        String nomFichier = (nomFichierOriginal == null || nomFichierOriginal.isBlank())
                ? "document"
                : nomFichierOriginal;
        TypeDocument type = determinerType(nomFichier, typeContenu);

        String chemin = stockageDocument.sauvegarder(sessionId, nomFichier, contenu);
        Document document = new Document(sessionId, type, nomFichier, chemin);
        Document sauvegarde = documentRepository.save(document);

        eventPublisher.publishEvent(new DocumentTeleverseEvent(sauvegarde.getId()));
        return sauvegarde;
    }

    private TypeDocument determinerType(String nomFichier, String typeContenu) {
        if (typeContenu != null && typeContenu.equalsIgnoreCase("application/pdf")) {
            return TypeDocument.PDF;
        }
        if (nomFichier.toLowerCase().endsWith(".pdf")) {
            return TypeDocument.PDF;
        }
        return TypeDocument.PHOTO;
    }

    @Async
    @EventListener
    public void surDocumentTeleverse(DocumentTeleverseEvent evenement) {
        Document document = documentRepository.findById(evenement.documentId()).orElse(null);
        if (document == null) {
            return;
        }

        try {
            byte[] contenu = Files.readAllBytes(Path.of(document.getCheminStockage()));
            String texte = extracteurDocument.extraireTexte(contenu);
            document.marquerReussi(texte);
        } catch (IOException | RuntimeException e) {
            LOG.warn("Echec de l'extraction du document {}", document.getId(), e);
            document.marquerEchec();
        }
        documentRepository.save(document);
    }

    public List<Document> obtenirDocuments(UUID sessionId) {
        sessionService.obtenirSession(sessionId);
        return documentRepository.findBySessionIdOrderByDateCreationAsc(sessionId);
    }
}
