package com.memoria.core.transcription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class TranscripteurAzureSpeech implements TranscripteurPort {

    private static final Logger LOG = LoggerFactory.getLogger(TranscripteurAzureSpeech.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String API_VERSION = "2025-10-15";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String cle;
    private final String region;
    private final String langue;
    private final int maxLocuteurs;

    public TranscripteurAzureSpeech(
            @Value("${azure.speech.key}") String cle,
            @Value("${azure.speech.region}") String region,
            @Value("${azure.speech.langue:fr-FR}") String langue,
            @Value("${azure.speech.max-locuteurs:4}") int maxLocuteurs
    ) {
        this.cle = cle;
        this.region = region;
        this.langue = langue;
        this.maxLocuteurs = maxLocuteurs;

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
    public ResultatTranscription transcrire(byte[] audio) {
        if (cle == null || cle.isBlank() || region == null || region.isBlank()) {
            return new ResultatTranscription(
                    "Transcription hors ligne : les credentials Azure Speech ne sont pas configures.",
                    List.of()
            );
        }

        String frontiere = "MemoriaBoundary" + UUID.randomUUID();
        String definition = "{\"locales\":[\"" + langue + "\"],\"diarization\":{\"maxSpeakers\":"
                + maxLocuteurs + ",\"enabled\":true}}";
        byte[] corps = construireCorpsMultipart(frontiere, audio, definition);

        URI uri = URI.create(
                "https://" + region + ".api.cognitive.microsoft.com/speechtotext/transcriptions:transcribe"
                        + "?api-version=" + API_VERSION
        );
        HttpRequest requete = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(90))
                .header("Ocp-Apim-Subscription-Key", cle)
                .header("Content-Type", "multipart/form-data; boundary=" + frontiere)
                .POST(HttpRequest.BodyPublishers.ofByteArray(corps))
                .build();

        try {
            HttpResponse<String> reponse = httpClient.send(requete, HttpResponse.BodyHandlers.ofString());
            if (reponse.statusCode() != 200) {
                throw new TranscriptionException(
                        "Azure Speech a repondu avec le statut " + reponse.statusCode() + " : " + reponse.body()
                );
            }
            return extraireResultat(reponse.body());
        } catch (IOException e) {
            throw new TranscriptionException("Echec de l'appel a Azure Speech", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TranscriptionException("Appel a Azure Speech interrompu", e);
        }
    }

    private byte[] construireCorpsMultipart(String frontiere, byte[] audio, String definition) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            String crlf = "\r\n";

            out.write(("--" + frontiere + crlf).getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"audio\"; filename=\"audio.wav\"" + crlf)
                    .getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Type: application/octet-stream" + crlf + crlf).getBytes(StandardCharsets.UTF_8));
            out.write(audio);
            out.write(crlf.getBytes(StandardCharsets.UTF_8));

            out.write(("--" + frontiere + crlf).getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"definition\"" + crlf + crlf)
                    .getBytes(StandardCharsets.UTF_8));
            out.write(definition.getBytes(StandardCharsets.UTF_8));
            out.write(crlf.getBytes(StandardCharsets.UTF_8));

            out.write(("--" + frontiere + "--" + crlf).getBytes(StandardCharsets.UTF_8));
            return out.toByteArray();
        } catch (IOException e) {
            throw new TranscriptionException("Echec de construction de la requete Azure Speech", e);
        }
    }

    private ResultatTranscription extraireResultat(String corpsReponse) throws IOException {
        JsonNode racine = JSON.readTree(corpsReponse);

        String texteComplet = racine.path("combinedPhrases").path(0).path("text").asText();

        List<SegmentLocuteur> segments = new ArrayList<>();
        for (JsonNode phrase : racine.path("phrases")) {
            segments.add(new SegmentLocuteur(
                    phrase.path("speaker").asInt(0),
                    phrase.path("text").asText(),
                    phrase.path("offsetMilliseconds").asLong(0),
                    phrase.path("durationMilliseconds").asLong(0)
            ));
        }

        return new ResultatTranscription(texteComplet, segments);
    }
}
