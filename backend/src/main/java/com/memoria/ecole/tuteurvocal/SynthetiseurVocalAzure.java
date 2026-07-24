package com.memoria.ecole.tuteurvocal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

// Miroir de TranscripteurAzureSpeech (meme ressource Azure Speech, cle/region
// reutilisees, pas de nouvelle config) mais pour la synthese vocale (TTS),
// premiere implementation TTS de ce projet -- voir
// docs/phases/phase-9-tuteur-vocal.md.
@Component
public class SynthetiseurVocalAzure implements SynthetiseurVocalPort {

    private static final Logger LOG = LoggerFactory.getLogger(SynthetiseurVocalAzure.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String cle;
    private final String region;
    private final String voix;

    public SynthetiseurVocalAzure(
            @Value("${azure.speech.key}") String cle,
            @Value("${azure.speech.region}") String region,
            @Value("${azure.speech.tts.voix:fr-FR-DeniseNeural}") String voix
    ) {
        this.cle = cle;
        this.region = region;
        this.voix = voix;

        if (cle == null || cle.isBlank() || region == null || region.isBlank()) {
            LOG.warn(
                    "azure.speech.key ou azure.speech.region est vide : la synthese vocale du tuteur "
                            + "echouera tant que AZURE_SPEECH_KEY / AZURE_SPEECH_REGION ne sont pas "
                            + "definies dans l'environnement qui lance l'application."
            );
        }
    }

    // Contrairement a TranscripteurAzureSpeech (qui degrade en placeholder
    // silencieux quand les credentials manquent), on leve ici : une reponse
    // du tuteur sans audio est un tour casse, pas degradable silencieusement.
    @Override
    public byte[] synthetiser(String texte) {
        if (cle == null || cle.isBlank() || region == null || region.isBlank()) {
            throw new SyntheseVocaleException("Azure Speech non configure (AZURE_SPEECH_KEY / AZURE_SPEECH_REGION manquants)");
        }

        String ssml = "<speak version='1.0' xml:lang='fr-FR'>"
                + "<voice xml:lang='fr-FR' name='" + voix + "'>" + echapperXml(texte) + "</voice>"
                + "</speak>";

        URI uri = URI.create("https://" + region + ".tts.speech.microsoft.com/cognitiveservices/v1");
        HttpRequest requete = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Ocp-Apim-Subscription-Key", cle)
                .header("Content-Type", "application/ssml+xml")
                .header("X-Microsoft-OutputFormat", "audio-16khz-32kbitrate-mono-mp3")
                .header("User-Agent", "MemoriaTuteurVocal")
                .POST(HttpRequest.BodyPublishers.ofString(ssml, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<byte[]> reponse = httpClient.send(requete, HttpResponse.BodyHandlers.ofByteArray());
            if (reponse.statusCode() != 200) {
                throw new SyntheseVocaleException(
                        "Azure Speech (TTS) a repondu avec le statut " + reponse.statusCode()
                );
            }
            return reponse.body();
        } catch (IOException e) {
            throw new SyntheseVocaleException("Echec de l'appel a Azure Speech (TTS)", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SyntheseVocaleException("Appel a Azure Speech (TTS) interrompu", e);
        }
    }

    private String echapperXml(String texte) {
        return texte
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
