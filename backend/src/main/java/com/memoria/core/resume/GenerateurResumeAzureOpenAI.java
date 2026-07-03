package com.memoria.core.resume;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class GenerateurResumeAzureOpenAI implements GenerateurResumePort {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String CONSIGNE_SYSTEME = """
            Tu es un assistant qui resume des transcriptions de reunions ou de cours, en francais.
            Reponds UNIQUEMENT avec un objet JSON valide de la forme exacte :
            {"resume": "un paragraphe de synthese fidele au contenu", "points_cles": ["point 1", "point 2"]}
            Aucun texte avant ou apres le JSON, aucun bloc de code markdown.
            """;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String endpoint;
    private final String cle;
    private final String modele;

    public GenerateurResumeAzureOpenAI(
            @Value("${azure.openai.endpoint}") String endpoint,
            @Value("${azure.openai.key}") String cle,
            @Value("${azure.openai.deployment}") String modele
    ) {
        this.endpoint = endpoint;
        this.cle = cle;
        this.modele = modele;
    }

    @Override
    public ResumeGenere genererResume(String transcriptComplet) {
        ObjectNode corpsRequete = JSON.createObjectNode();
        corpsRequete.put("model", modele);
        corpsRequete.put("instructions", CONSIGNE_SYSTEME);
        corpsRequete.put("input", transcriptComplet);

        try {
            HttpRequest requete = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(60))
                    .header("api-key", cle)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(corpsRequete)))
                    .build();

            HttpResponse<String> reponse = httpClient.send(requete, HttpResponse.BodyHandlers.ofString());
            if (reponse.statusCode() != 200) {
                throw new GenerationResumeException(
                        "Azure OpenAI a repondu avec le statut " + reponse.statusCode() + " : " + reponse.body()
                );
            }

            JsonNode corpsReponse = JSON.readTree(reponse.body());
            String contenu = extraireTexteDeSortie(corpsReponse);
            return extraireResume(contenu);
        } catch (IOException e) {
            throw new GenerationResumeException("Echec de l'appel a Azure OpenAI", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GenerationResumeException("Appel a Azure OpenAI interrompu", e);
        }
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
        throw new GenerationResumeException("Reponse d'Azure OpenAI sans message exploitable : " + corpsReponse);
    }

    private ResumeGenere extraireResume(String contenu) {
        String nettoye = contenu.strip();
        if (nettoye.startsWith("```")) {
            nettoye = nettoye.replaceFirst("^```(json)?", "").replaceFirst("```$", "").strip();
        }

        try {
            JsonNode noeud = JSON.readTree(nettoye);
            String texteResume = noeud.path("resume").asText();
            List<String> pointsCles = new ArrayList<>();
            noeud.path("points_cles").forEach(pointCle -> pointsCles.add(pointCle.asText()));
            return new ResumeGenere(texteResume, pointsCles);
        } catch (IOException e) {
            throw new GenerationResumeException("Reponse d'Azure OpenAI non exploitable : " + contenu, e);
        }
    }
}
