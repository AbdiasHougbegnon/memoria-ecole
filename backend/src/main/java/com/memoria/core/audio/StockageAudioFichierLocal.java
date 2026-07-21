package com.memoria.core.audio;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Component
public class StockageAudioFichierLocal implements StockageAudioPort {

    private final Path repertoireBase;

    public StockageAudioFichierLocal(
            @Value("${memoria.stockage.audio.repertoire-base:./data/audio}") String repertoireBase
    ) {
        this.repertoireBase = Paths.get(repertoireBase);
    }

    @Override
    public String sauvegarder(UUID sessionId, int numeroSequence, byte[] donnees) {
        try {
            Path repertoireSession = repertoireBase.resolve(sessionId.toString());
            Files.createDirectories(repertoireSession);
            Path fichierChunk = repertoireSession.resolve(numeroSequence + ".chunk");
            Files.write(fichierChunk, donnees);
            return fichierChunk.toString();
        } catch (IOException e) {
            throw new StockageAudioException("Echec de l'ecriture du chunk audio sur le disque", e);
        }
    }

    @Override
    public byte[] lire(String cheminStockage) {
        try {
            return Files.readAllBytes(Paths.get(cheminStockage));
        } catch (IOException e) {
            throw new StockageAudioException("Echec de la lecture du chunk audio sur le disque", e);
        }
    }
}
