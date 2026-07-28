package com.memoria.ecole.exercice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memoria.core.cout.CoutAzureService;
import com.memoria.core.cout.ServiceAzure;
import com.memoria.ecole.notion.NiveauMaitrise;

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

// Meme ressource Azure OpenAI ("Responses API") que
// GenerateurExerciceSaisieLibreAzureOpenAI/GenerateurQcmAzureOpenAI, avec son
// propre schema JSON : corrige le travail qu'un etudiant a fait sur papier,
// pas seulement l'evaluer par rapport a des elements de reponse attendus
// (contrairement a evaluerReponse, il n'y a pas d'enonce de reference ici,
// seulement le texte extrait de la photo).
@Component
public class CorrecteurTravailPapierAzureOpenAI implements CorrecteurTravailPapierPort {

    private static final Logger LOG = LoggerFactory.getLogger(CorrecteurTravailPapierAzureOpenAI.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String CONSIGNE = """
            Tu es un assistant pedagogique qui corrige le travail qu'un etudiant a fait sur
            papier, a partir du texte extrait d'une photo de sa copie (peut contenir des
            imperfections d'OCR mineures a ignorer), en francais. Identifie ce que l'etudiant a
            essaye de faire, signale precisement les erreurs, explique la correction attendue
            point par point, et evalue son niveau de maitrise global.
            Reponds UNIQUEMENT avec un objet JSON valide de la forme exacte :
            {
              "niveau": "NON_ABORDEE" | "EN_COURS" | "MAITRISEE",
              "correction": "la correction detaillee, claire et constructive pour l'etudiant"
            }
            "NON_ABORDEE" si le travail est vide, illisible ou ne montre aucune tentative
            pertinente. "EN_COURS" si le travail est partiellement correct ou incomplet.
            "MAITRISEE" si le travail est correct dans son ensemble.
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

    public CorrecteurTravailPapierAzureOpenAI(
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
                            + "la correction de travail papier echouera tant que AZURE_OPENAI_ENDPOINT / "
                            + "AZURE_OPENAI_KEY / AZURE_OPENAI_DEPLOYMENT ne sont pas definies dans "
                            + "l'environnement qui lance l'application."
            );
        }
    }

    @Override
    public CorrectionTravailPapier corriger(String texteExtrait) {
        var corpsRequete = JSON.createObjectNode();
        corpsRequete.put("model", modele);
        corpsRequete.put("instructions", CONSIGNE);
        corpsRequete.put("input", texteExtrait);

        try {
            HttpRequest requete = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(120))
                    .header("api-key", cle)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(corpsRequete)))
                    .build();

            HttpResponse<String> reponse = httpClient.send(requete, HttpResponse.BodyHandlers.ofString());
            if (reponse.statusCode() != 200) {
                throw new GenerationExerciceException(
                        "Azure OpenAI a repondu avec le statut " + reponse.statusCode() + " : " + reponse.body()
                );
            }

            JsonNode corpsReponse = JSON.readTree(reponse.body());
            enregistrerCout(corpsReponse);
            return extraireCorrection(extraireTexteDeSortie(corpsReponse));
        } catch (IOException e) {
            throw new GenerationExerciceException("Echec de l'appel a Azure OpenAI", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GenerationExerciceException("Appel a Azure OpenAI interrompu", e);
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
        throw new GenerationExerciceException("Reponse d'Azure OpenAI sans message exploitable : " + corpsReponse);
    }

    private String nettoyer(String contenu) {
        String nettoye = contenu.strip();
        if (nettoye.startsWith("```")) {
            nettoye = nettoye.replaceFirst("^```(json)?", "").replaceFirst("```$", "").strip();
        }
        return nettoye;
    }

    private CorrectionTravailPapier extraireCorrection(String contenu) {
        try {
            JsonNode noeud = JSON.readTree(nettoyer(contenu));
            NiveauMaitrise niveau = NiveauMaitrise.valueOf(noeud.path("niveau").asText());
            return new CorrectionTravailPapier(niveau, noeud.path("correction").asText());
        } catch (IOException | IllegalArgumentException e) {
            throw new GenerationExerciceException("Reponse d'Azure OpenAI non exploitable : " + contenu, e);
        }
    }
}
