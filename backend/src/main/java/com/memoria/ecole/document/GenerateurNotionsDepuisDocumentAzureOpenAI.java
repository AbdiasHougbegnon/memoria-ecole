package com.memoria.ecole.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memoria.core.cout.CoutAzureService;
import com.memoria.core.cout.ServiceAzure;

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
import java.util.ArrayList;
import java.util.List;

// Miroir de GenerateurResumeCoursAzureOpenAI/GenerateurTourTuteurAzureOpenAI
// (meme HttpClient, meme ressource Azure OpenAI "Responses API", meme pattern
// JSON strict en sortie et meme suivi de cout).
@Component
public class GenerateurNotionsDepuisDocumentAzureOpenAI implements GenerateurNotionsDepuisDocumentPort {

    private static final Logger LOG = LoggerFactory.getLogger(GenerateurNotionsDepuisDocumentAzureOpenAI.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String CONSIGNE = """
            Tu es un assistant pedagogique qui extrait les notions cles (terme + definition
            concise) a partir du texte d'une fiche de cours, d'exercices ou d'une epreuve, en
            francais.
            Reponds UNIQUEMENT avec un objet JSON valide de la forme exacte :
            {
              "notions": [
                {"terme": "nom de la notion ou du concept aborde", "definition": "definition concise telle qu'elle ressort du texte"}
              ]
            }
            Ne liste que des notions reellement presentes dans le texte, n'invente rien. Si aucune
            notion claire n'est identifiable, renvoie un tableau vide pour le champ "notions".
            Aucun texte avant ou apres le JSON, aucun bloc de code markdown.
            """;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String endpoint;
    private final String cle;
    private final String modele;
    private final CoutAzureService coutAzureService;
    private final double eurosPar1000Tokens;

    public GenerateurNotionsDepuisDocumentAzureOpenAI(
            @Value("${azure.openai.endpoint}") String endpoint,
            @Value("${azure.openai.key}") String cle,
            @Value("${azure.openai.deployment}") String modele,
            @Value("${memoria.cout.azure.openai.euros-par-1k-tokens:0.002}") double eurosPar1000Tokens,
            CoutAzureService coutAzureService
    ) {
        this.endpoint = endpoint;
        this.cle = cle;
        this.modele = modele;
        this.coutAzureService = coutAzureService;
        this.eurosPar1000Tokens = eurosPar1000Tokens;

        if (endpoint == null || endpoint.isBlank() || cle == null || cle.isBlank()
                || modele == null || modele.isBlank()) {
            LOG.warn(
                    "azure.openai.endpoint, azure.openai.key ou azure.openai.deployment est vide : "
                            + "l'extraction de notions candidates echouera tant que AZURE_OPENAI_ENDPOINT / "
                            + "AZURE_OPENAI_KEY / AZURE_OPENAI_DEPLOYMENT ne sont pas definies dans "
                            + "l'environnement qui lance l'application."
            );
        }
    }

    @Override
    public List<CandidatNotionGenere> genererNotionsCandidates(String texteDocument) {
        var corpsRequete = JSON.createObjectNode();
        corpsRequete.put("model", modele);
        corpsRequete.put("instructions", CONSIGNE);
        corpsRequete.put("input", texteDocument);

        try {
            HttpRequest requete = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(120))
                    .header("api-key", cle)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(corpsRequete)))
                    .build();

            HttpResponse<String> reponse = httpClient.send(requete, HttpResponse.BodyHandlers.ofString());
            if (reponse.statusCode() != 200) {
                throw new GenerationNotionsDepuisDocumentException(
                        "Azure OpenAI a repondu avec le statut " + reponse.statusCode() + " : " + reponse.body()
                );
            }

            JsonNode corpsReponse = JSON.readTree(reponse.body());
            enregistrerCout(corpsReponse);
            String contenu = extraireTexteDeSortie(corpsReponse);
            return extraireCandidats(contenu);
        } catch (IOException e) {
            throw new GenerationNotionsDepuisDocumentException("Echec de l'appel a Azure OpenAI", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GenerationNotionsDepuisDocumentException("Appel a Azure OpenAI interrompu", e);
        }
    }

    private void enregistrerCout(JsonNode corpsReponse) {
        int tokens = corpsReponse.path("usage").path("total_tokens").asInt(-1);
        double coutEuros = tokens >= 0
                ? (tokens / 1000.0) * eurosPar1000Tokens
                : coutAzureService.coutForfaitaireEuros();
        coutAzureService.enregistrerAppel(ServiceAzure.OPENAI_CHAT, coutEuros);
    }

    private String extraireTexteDeSortie(JsonNode corpsReponse) {
        for (JsonNode element : corpsReponse.path("output")) {
            if (!"message".equals(element.path("type").asText())) {
                continue;
            }
            for (JsonNode contenu : element.path("content")) {
                if ("output_text".equals(contenu.path("type").asText())) {
                    return contenu.path("text").asText();
                }
            }
        }
        throw new GenerationNotionsDepuisDocumentException("Reponse d'Azure OpenAI sans message exploitable : " + corpsReponse);
    }

    private List<CandidatNotionGenere> extraireCandidats(String contenu) {
        String nettoye = contenu.strip();
        if (nettoye.startsWith("```")) {
            nettoye = nettoye.replaceFirst("^```(json)?", "").replaceFirst("```$", "").strip();
        }

        try {
            JsonNode noeud = JSON.readTree(nettoye);
            List<CandidatNotionGenere> candidats = new ArrayList<>();
            noeud.path("notions").forEach(notion -> candidats.add(new CandidatNotionGenere(
                    notion.path("terme").asText(),
                    notion.path("definition").asText()
            )));
            return candidats;
        } catch (IOException e) {
            throw new GenerationNotionsDepuisDocumentException("Reponse d'Azure OpenAI non exploitable : " + contenu, e);
        }
    }
}
