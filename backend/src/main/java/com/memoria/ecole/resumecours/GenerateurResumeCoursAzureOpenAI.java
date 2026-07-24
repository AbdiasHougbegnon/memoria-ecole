package com.memoria.ecole.resumecours;

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
public class GenerateurResumeCoursAzureOpenAI implements GenerateurResumeCoursPort {

    private static final Logger LOG = LoggerFactory.getLogger(GenerateurResumeCoursAzureOpenAI.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String CONSIGNE = """
            Tu es un assistant pedagogique qui aide des etudiants a reviser un cours a partir de sa
            transcription, en francais. La transcription peut contenir des reperes "Intervenant N" :
            ignore-les, ce ne sont pas des notions a retenir.
            Reponds UNIQUEMENT avec un objet JSON valide de la forme exacte :
            {
              "synthese": "un paragraphe qui resume fidelement le contenu du cours",
              "notions": [
                {"terme": "nom de la notion ou du concept aborde", "definition": "la definition ou l'explication telle que donnee dans le cours"}
              ],
              "points_a_revoir": ["point signale comme important a reviser, ou notion peu approfondie"]
            }
            Ne liste que des notions reellement expliquees dans la transcription, n'invente rien. Si
            aucune notion ou aucun point a reviser n'est identifiable, renvoie un tableau vide pour le
            champ correspondant.
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

    public GenerateurResumeCoursAzureOpenAI(
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
                            + "la generation de resume de cours echouera tant que AZURE_OPENAI_ENDPOINT / "
                            + "AZURE_OPENAI_KEY / AZURE_OPENAI_DEPLOYMENT ne sont pas definies dans "
                            + "l'environnement qui lance l'application."
            );
        }
    }

    @Override
    public ResumeCoursGenere genererResumeCours(String transcriptComplet) {
        // Meme ressource Azure OpenAI ("Responses API") que
        // GenerateurCompteRenduAzureOpenAI, avec un schema JSON different.
        var corpsRequete = JSON.createObjectNode();
        corpsRequete.put("model", modele);
        corpsRequete.put("instructions", CONSIGNE);
        corpsRequete.put("input", transcriptComplet);

        try {
            // Meme delai genereux que le compte rendu complet : sortie
            // structuree comparable (synthese + notions + points a revoir).
            HttpRequest requete = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(120))
                    .header("api-key", cle)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(corpsRequete)))
                    .build();

            HttpResponse<String> reponse = httpClient.send(requete, HttpResponse.BodyHandlers.ofString());
            if (reponse.statusCode() != 200) {
                throw new GenerationResumeCoursException(
                        "Azure OpenAI a repondu avec le statut " + reponse.statusCode() + " : " + reponse.body()
                );
            }

            JsonNode corpsReponse = JSON.readTree(reponse.body());
            enregistrerCout(corpsReponse);
            String contenu = extraireTexteDeSortie(corpsReponse);
            return extraireResumeCours(contenu);
        } catch (IOException e) {
            throw new GenerationResumeCoursException("Echec de l'appel a Azure OpenAI", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GenerationResumeCoursException("Appel a Azure OpenAI interrompu", e);
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
        throw new GenerationResumeCoursException("Reponse d'Azure OpenAI sans message exploitable : " + corpsReponse);
    }

    private ResumeCoursGenere extraireResumeCours(String contenu) {
        String nettoye = contenu.strip();
        if (nettoye.startsWith("```")) {
            nettoye = nettoye.replaceFirst("^```(json)?", "").replaceFirst("```$", "").strip();
        }

        try {
            JsonNode noeud = JSON.readTree(nettoye);
            String synthese = noeud.path("synthese").asText();

            List<NotionExtraite> notions = new ArrayList<>();
            noeud.path("notions").forEach(notion -> notions.add(new NotionExtraite(
                    notion.path("terme").asText(),
                    notion.path("definition").asText()
            )));

            List<String> pointsARevoir = new ArrayList<>();
            noeud.path("points_a_revoir").forEach(point -> pointsARevoir.add(point.asText()));

            return new ResumeCoursGenere(synthese, notions, pointsARevoir);
        } catch (IOException e) {
            throw new GenerationResumeCoursException("Reponse d'Azure OpenAI non exploitable : " + contenu, e);
        }
    }
}
