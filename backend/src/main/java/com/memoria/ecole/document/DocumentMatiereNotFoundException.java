package com.memoria.ecole.document;

import java.util.UUID;

public class DocumentMatiereNotFoundException extends RuntimeException {

    public DocumentMatiereNotFoundException(UUID id) {
        super("Document de matiere introuvable : " + id);
    }
}
