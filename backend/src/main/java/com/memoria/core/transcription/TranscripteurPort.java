package com.memoria.core.transcription;

// Detail d'infrastructure remplacable (Azure Speech aujourd'hui, un autre fournisseur demain).
public interface TranscripteurPort {

    ResultatTranscription transcrire(byte[] audio);
}
