package com.memoria.core.audio;

import java.util.UUID;

// Detail d'infrastructure remplacable (fichier local aujourd'hui, Azure Blob demain).
public interface StockageAudioPort {

    String sauvegarder(UUID sessionId, int numeroSequence, byte[] donnees);
}
