package com.memoria.core.transcription;

import com.memoria.core.cout.CoutAzureService;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TranscripteurAzureSpeechTest {

    @Test
    void transcrire_retourne_un_texte_de_fallback_quand_azure_nest_pas_configure() {
        CoutAzureService coutAzureService = new CoutAzureService(new SimpleMeterRegistry(), 100.0, false, 0.01);
        TranscripteurAzureSpeech transcripteur = new TranscripteurAzureSpeech("", "", "fr-FR", 4, 1.0, 30, coutAzureService);

        ResultatTranscription resultat = transcripteur.transcrire(new byte[]{1, 2, 3});

        assertThat(resultat.texteComplet()).contains("hors ligne");
        assertThat(resultat.segments()).isEmpty();
    }
}
