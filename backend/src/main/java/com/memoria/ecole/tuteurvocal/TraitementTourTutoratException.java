package com.memoria.ecole.tuteurvocal;

// Enveloppe les echecs IA/STT lors du traitement d'un tour (contrairement a
// la transcription du moteur, qui degrade en placeholder silencieux, un tour
// de tutorat casse doit etre visible cote etudiant -- voir
// GenerateurTourTuteurAzureOpenAI et TranscripteurAzureSpeech).
public class TraitementTourTutoratException extends RuntimeException {

    public TraitementTourTutoratException(String message, Throwable cause) {
        super(message, cause);
    }
}
