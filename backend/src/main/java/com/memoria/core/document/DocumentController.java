package com.memoria.core.document;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse televerser(
            @PathVariable UUID sessionId,
            @RequestParam("fichier") MultipartFile fichier
    ) throws IOException {
        Document document = documentService.televerser(
                sessionId, fichier.getOriginalFilename(), fichier.getContentType(), fichier.getBytes()
        );
        return DocumentResponse.depuis(document);
    }

    @GetMapping
    public List<DocumentResponse> obtenirDocuments(@PathVariable UUID sessionId) {
        return documentService.obtenirDocuments(sessionId).stream()
                .map(DocumentResponse::depuis)
                .toList();
    }
}
