package com.memoria.core.document;

import java.util.UUID;

// Detail d'infrastructure remplacable (fichier local aujourd'hui, Azure Blob demain).
public interface StockageDocumentPort {

    String sauvegarder(UUID sessionId, String nomFichier, byte[] contenu);
}
