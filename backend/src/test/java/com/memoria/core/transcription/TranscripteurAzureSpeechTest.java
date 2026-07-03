package com.memoria.core.transcription;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TranscripteurAzureSpeechTest {

    @Test
    void transcrire_retourne_un_texte_de_fallback_quand_azure_nest_pas_configure() {
        TranscripteurAzureSpeech transcripteur = new TranscripteurAzureSpeech("", "", "fr-FR", "audio/webm; codecs=opus");

        String texte = transcripteur.transcrire(new byte[]{1, 2, 3});

        assertThat(texte).contains("hors ligne");
    }

    @Test
    void convertirEnWav_conserve_un_flux_wav_deja_valide() {
        TranscripteurAzureSpeech transcripteur = new TranscripteurAzureSpeech("", "", "fr-FR", "audio/wav; codecs=audio/pcm; samplerate=16000");
        byte[] wav = new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'};

        byte[] resultat = transcripteur.convertirEnWav(wav);

        assertThat(resultat).isEqualTo(wav);
    }
}
