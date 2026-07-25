package com.memoria.ecole.qcm;

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

@Component
public class GenerateurQcmAzureOpenAI implements GenerateurQcmPort {

    private static final Logger LOG = LoggerFactory.getLogger(GenerateurQcmAzureOpenAI.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String CONSIGNE = """
            Tu es un assistant pedagogique qui construit un QCM de revision a partir de la synthese
            et des notions d'un cours, en francais. Genere exactement 5 questions a choix multiple,
            chacune avec exactement 4 propositions de reponse dont une seule est correcte.
            Reponds UNIQUEMENT avec un objet JSON valide de la forme exacte :
            {
              "questions": [
                {
                  "enonce": "l'enonce de la question",
                  "choix": ["proposition A", "proposition B", "proposition C", "proposition D"],
                  "reponse_correcte": 0,
                  "explication": "pourquoi cette reponse est correcte, en s'appuyant sur le cours"
                }
              ]
            }
            "reponse_correcte" est l'index (0 a 3) de la bonne proposition dans le tableau "choix".
            Ne porte que sur des notions reellement presentes dans le contenu fourni, n'invente rien.
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

    public GenerateurQcmAzureOpenAI(
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
                            + "la generation de QCM echouera tant que AZURE_OPENAI_ENDPOINT / "
                            + "AZURE_OPENAI_KEY / AZURE_OPENAI_DEPLOYMENT ne sont pas definies dans "
                            + "l'environnement qui lance l'application."
            );
        }
    }

    @Override
    public QcmGenere genererQcm(String contenuCours) {
        // Meme ressource Azure OpenAI ("Responses API") que
        // GenerateurResumeCoursAzureOpenAI, avec un schema JSON different.
        var corpsRequete = JSON.createObjectNode();
        corpsRequete.put("model", modele);
        corpsRequete.put("instructions", CONSIGNE);
        corpsRequete.put("input", contenuCours);

        try {
            HttpRequest requete = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(120))
                    .header("api-key", cle)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(corpsRequete)))
                    .build();

            HttpResponse<String> reponse = httpClient.send(requete, HttpResponse.BodyHandlers.ofString());
            if (reponse.statusCode() != 200) {
                throw new GenerationQcmException(
                        "Azure OpenAI a repondu avec le statut " + reponse.statusCode() + " : " + reponse.body()
                );
            }

            JsonNode corpsReponse = JSON.readTree(reponse.body());
            enregistrerCout(corpsReponse);
            String contenu = extraireTexteDeSortie(corpsReponse);
            return extraireQcm(contenu);
        } catch (IOException e) {
            throw new GenerationQcmException("Echec de l'appel a Azure OpenAI", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GenerationQcmException("Appel a Azure OpenAI interrompu", e);
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
        throw new GenerationQcmException("Reponse d'Azure OpenAI sans message exploitable : " + corpsReponse);
    }

    private QcmGenere extraireQcm(String contenu) {
        String nettoye = contenu.strip();
        if (nettoye.startsWith("```")) {
            nettoye = nettoye.replaceFirst("^```(json)?", "").replaceFirst("```$", "").strip();
        }

        try {
            JsonNode noeud = JSON.readTree(nettoye);

            List<QuestionExtraite> questions = new ArrayList<>();
            noeud.path("questions").forEach(question -> {
                List<String> choix = new ArrayList<>();
                question.path("choix").forEach(choixNode -> choix.add(choixNode.asText()));
                questions.add(new QuestionExtraite(
                        question.path("enonce").asText(),
                        choix,
                        question.path("reponse_correcte").asInt(),
                        question.path("explication").asText()
                ));
            });

            return new QcmGenere(questions);
        } catch (IOException e) {
            throw new GenerationQcmException("Reponse d'Azure OpenAI non exploitable : " + contenu, e);
        }
    }
}
