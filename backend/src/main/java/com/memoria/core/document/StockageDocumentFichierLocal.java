package com.memoria.core.document;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Component
public class StockageDocumentFichierLocal implements StockageDocumentPort {

    private final Path repertoireBase;

    public StockageDocumentFichierLocal(
            @Value("${memoria.stockage.document.repertoire-base:./data/documents}") String repertoireBase
    ) {
        this.repertoireBase = Paths.get(repertoireBase);
    }

    @Override
    public String sauvegarder(UUID sessionId, String nomFichier, byte[] contenu) {
        try {
            Path repertoireSession = repertoireBase.resolve(sessionId.toString());
            Files.createDirectories(repertoireSession);
            // Prefixe par un UUID pour eviter toute collision si deux fichiers
            // du meme nom sont deposes dans la meme session.
            Path fichier = repertoireSession.resolve(UUID.randomUUID() + "_" + nomFichier);
            Files.write(fichier, contenu);
            return fichier.toString();
        } catch (IOException e) {
            throw new StockageDocumentException("Echec de l'ecriture du document sur le disque", e);
        }
    }
}
