package com.memoria.core.document;

// Detail d'infrastructure remplacable (Azure Document Intelligence aujourd'hui, un autre fournisseur demain).
public interface ExtracteurDocumentPort {

    String extraireTexte(byte[] contenu);
}
