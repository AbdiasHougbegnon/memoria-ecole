package com.memoria.core.transcription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class TranscripteurAzureSpeech implements TranscripteurPort {

    private static final Logger LOG = LoggerFactory.getLogger(TranscripteurAzureSpeech.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String cle;
    private final String region;
    private final String langue;
    private final String typeContenu;

    public TranscripteurAzureSpeech(
            @Value("${azure.speech.key}") String cle,
            @Value("${azure.speech.region}") String region,
            @Value("${azure.speech.langue:fr-FR}") String langue,
            @Value("${azure.speech.content-type:audio/wav; codecs=audio/pcm; samplerate=16000}") String typeContenu
    ) {
        this.cle = cle;
        this.region = region;
        this.langue = langue;
        this.typeContenu = typeContenu;

        if (cle == null || cle.isBlank() || region == null || region.isBlank()) {
            LOG.warn(
                    "azure.speech.key ou azure.speech.region est vide : toutes les transcriptions "
                            + "echoueront tant que AZURE_SPEECH_KEY / AZURE_SPEECH_REGION ne sont pas "
                            + "definies dans l'environnement qui lance l'application (redemarrer le "
                            + "terminal/IDE apres un setx ne suffit pas, il faut aussi relancer l'appli)."
            );
        }
    }

    @Override
    public String transcrire(byte[] audio) {
        URI uri = URI.create(
                "https://" + region + ".stt.speech.microsoft.com/speech/recognition/conversation/cognitiveservices/v1"
                        + "?language=" + langue + "&format=simple"
        );
        HttpRequest requete = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Ocp-Apim-Subscription-Key", cle)
                .header("Content-Type", typeContenu)
                .POST(HttpRequest.BodyPublishers.ofByteArray(audio))
                .build();

        try {
            HttpResponse<String> reponse = httpClient.send(requete, HttpResponse.BodyHandlers.ofString());
            if (reponse.statusCode() != 200) {
                throw new TranscriptionException(
                        "Azure Speech a repondu avec le statut " + reponse.statusCode() + " : " + reponse.body()
                );
            }

            JsonNode corps = JSON.readTree(reponse.body());
            String statutReconnaissance = corps.path("RecognitionStatus").asText();
            if (!"Success".equals(statutReconnaissance)) {
                throw new TranscriptionException("Azure Speech n'a pas reconnu l'audio (statut : " + statutReconnaissance + ")");
            }
            return corps.path("DisplayText").asText();
        } catch (IOException e) {
            throw new TranscriptionException("Echec de l'appel a Azure Speech", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TranscriptionException("Appel a Azure Speech interrompu", e);
        }
    }
}
