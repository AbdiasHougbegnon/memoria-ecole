package com.memoria.core.document;

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
public class ExtracteurDocumentAzureDocumentIntelligence implements ExtracteurDocumentPort {

    private static final Logger LOG = LoggerFactory.getLogger(ExtracteurDocumentAzureDocumentIntelligence.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String API_VERSION = "2024-11-30";
    private static final int DELAI_POLLING_MILLISECONDES = 2000;
    private static final int MAX_TENTATIVES_POLLING = 30;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String endpoint;
    private final String cle;

    public ExtracteurDocumentAzureDocumentIntelligence(
            @Value("${azure.docintel.endpoint}") String endpoint,
            @Value("${azure.docintel.key}") String cle
    ) {
        this.endpoint = (endpoint == null || endpoint.isBlank() || endpoint.endsWith("/"))
                ? endpoint
                : endpoint + "/";
        this.cle = cle;

        if (cle == null || cle.isBlank() || endpoint == null || endpoint.isBlank()) {
            LOG.warn(
                    "azure.docintel.key ou azure.docintel.endpoint est vide : l'extraction de texte des "
                            + "documents echouera tant que AZURE_DOCINTEL_KEY / AZURE_DOCINTEL_ENDPOINT ne "
                            + "sont pas definies dans l'environnement qui lance l'application."
            );
        }
    }

    @Override
    public String extraireTexte(byte[] contenu) {
        if (cle == null || cle.isBlank() || endpoint == null || endpoint.isBlank()) {
            return "Extraction hors ligne : les credentials Azure Document Intelligence ne sont pas configures.";
        }

        try {
            String operationLocation = soumettreAnalyse(contenu);
            return attendreResultat(operationLocation);
        } catch (IOException e) {
            throw new ExtractionDocumentException("Echec de l'appel a Azure Document Intelligence", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExtractionDocumentException("Extraction de document interrompue", e);
        }
    }

    // L'analyse Azure Document Intelligence est asynchrone : on soumet le
    // fichier, Azure repond 202 avec une URL a interroger (Operation-Location),
    // puis on relit cette URL jusqu'a obtenir un statut final. Content-Type
    // "application/octet-stream" suffit -- Azure detecte le format (PDF,
    // JPEG, PNG...) directement depuis le contenu, verifie empiriquement.
    private String soumettreAnalyse(byte[] contenu) throws IOException, InterruptedException {
        URI uri = URI.create(
                endpoint + "documentintelligence/documentModels/prebuilt-read:analyze?api-version=" + API_VERSION
        );
        HttpRequest requete = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Ocp-Apim-Subscription-Key", cle)
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(contenu))
                .build();

        HttpResponse<Void> reponse = httpClient.send(requete, HttpResponse.BodyHandlers.discarding());
        if (reponse.statusCode() != 202) {
            throw new ExtractionDocumentException(
                    "Azure Document Intelligence a repondu avec le statut " + reponse.statusCode()
            );
        }
        return reponse.headers().firstValue("Operation-Location")
                .orElseThrow(() -> new ExtractionDocumentException(
                        "Reponse d'Azure Document Intelligence sans en-tete Operation-Location"
                ));
    }

    private String attendreResultat(String operationLocation) throws IOException, InterruptedException {
        HttpRequest requetePoll = HttpRequest.newBuilder(URI.create(operationLocation))
                .timeout(Duration.ofSeconds(30))
                .header("Ocp-Apim-Subscription-Key", cle)
                .GET()
                .build();

        for (int tentative = 0; tentative < MAX_TENTATIVES_POLLING; tentative++) {
            Thread.sleep(DELAI_POLLING_MILLISECONDES);

            HttpResponse<String> reponse = httpClient.send(requetePoll, HttpResponse.BodyHandlers.ofString());
            JsonNode racine = JSON.readTree(reponse.body());
            String statut = racine.path("status").asText();

            if ("succeeded".equals(statut)) {
                return racine.path("analyzeResult").path("content").asText();
            }
            if ("failed".equals(statut)) {
                throw new ExtractionDocumentException(
                        "Azure Document Intelligence a signale un echec : " + reponse.body()
                );
            }
            // "running" ou "notStarted" : on reessaie au tour suivant.
        }
        throw new ExtractionDocumentException("Delai d'attente depasse pour l'extraction du document");
    }
}
