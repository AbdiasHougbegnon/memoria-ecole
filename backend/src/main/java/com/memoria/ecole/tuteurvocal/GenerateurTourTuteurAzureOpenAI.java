package com.memoria.ecole.tuteurvocal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

// Miroir de GenerateurResumeCoursAzureOpenAI (meme HttpClient, meme ressource
// Azure OpenAI "Responses API", meme pattern JSON strict en sortie). Le
// prompt systeme porte TOUTE la logique pedagogique ("change d'approche,
// analogies, reformulation, ne lache pas une notion tant qu'elle n'est pas
// comprise") -- prompt engineering, pas une machine a etats explicite, voir
// limites documentees dans docs/phases/phase-9-tuteur-vocal.md.
@Component
public class GenerateurTourTuteurAzureOpenAI implements GenerateurTourTuteurPort {

    private static final Logger LOG = LoggerFactory.getLogger(GenerateurTourTuteurAzureOpenAI.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String CONSIGNE_TEMPLATE = """
            Tu es un tuteur vocal qui enseigne une notion a un etudiant, en francais, a l'oral
            (phrases courtes et naturelles, pas de listes a puces, pas de markdown -- ce texte
            sera lu a voix haute par un synthetiseur vocal).

            Notion enseignee : "%s" -- %s

            Mode : %s

            Ton but est que l'etudiant maitrise vraiment cette notion, pas seulement qu'il recite
            une reponse correcte une fois. Si sa reponse montre une incomprehension ou une
            confusion, NE PASSE PAS a la notion suivante : change d'approche (nouvelle analogie,
            reformulation plus simple, exemple concret different) plutot que de repeter la meme
            explication. Si sa reponse est correcte et montre une comprehension solide, felicite-le
            brievement et indique que la notion est maitrisee.

            Reponds UNIQUEMENT avec un objet JSON valide de la forme exacte :
            {
              "texte_tuteur": "ce que tu dis a l'etudiant maintenant (2-4 phrases orales)",
              "evaluation_maitrise": "NON_ABORDEE" ou "EN_COURS" ou "MAITRISEE",
              "notion_maitrisee": true ou false
            }
            Aucun texte avant ou apres le JSON, aucun bloc de code markdown.
            """;

    private static final String MODE_EXPLICATION = "tu expliques la notion et verifies la comprehension par des questions";
    private static final String MODE_EXERCICE = "tu poses des exercices/questions d'application sur cette notion, sans la re-expliquer d'abord";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String endpoint;
    private final String cle;
    private final String modele;

    public GenerateurTourTuteurAzureOpenAI(
            @Value("${azure.openai.endpoint}") String endpoint,
            @Value("${azure.openai.key}") String cle,
            @Value("${azure.openai.deployment}") String modele
    ) {
        this.endpoint = endpoint;
        this.cle = cle;
        this.modele = modele;

        if (endpoint == null || endpoint.isBlank() || cle == null || cle.isBlank()
                || modele == null || modele.isBlank()) {
            LOG.warn(
                    "azure.openai.endpoint, azure.openai.key ou azure.openai.deployment est vide : "
                            + "le tuteur vocal echouera tant que AZURE_OPENAI_ENDPOINT / AZURE_OPENAI_KEY / "
                            + "AZURE_OPENAI_DEPLOYMENT ne sont pas definies dans l'environnement qui lance "
                            + "l'application."
            );
        }
    }

    @Override
    public TourTuteurGenere genererTour(ContexteTour contexte) {
        String consigne = CONSIGNE_TEMPLATE.formatted(
                contexte.notionTerme(),
                contexte.notionDefinition(),
                contexte.mode() == ModeTutorat.EXERCICE ? MODE_EXERCICE : MODE_EXPLICATION
        );
        String input = construireInput(contexte);

        var corpsRequete = JSON.createObjectNode();
        corpsRequete.put("model", modele);
        corpsRequete.put("instructions", consigne);
        corpsRequete.put("input", input);

        try {
            HttpRequest requete = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(60))
                    .header("api-key", cle)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(corpsRequete)))
                    .build();

            HttpResponse<String> reponse = httpClient.send(requete, HttpResponse.BodyHandlers.ofString());
            if (reponse.statusCode() != 200) {
                throw new GenerationTourTuteurException(
                        "Azure OpenAI a repondu avec le statut " + reponse.statusCode() + " : " + reponse.body()
                );
            }

            JsonNode corpsReponse = JSON.readTree(reponse.body());
            String contenu = extraireTexteDeSortie(corpsReponse);
            return extraireTour(contenu);
        } catch (IOException e) {
            throw new GenerationTourTuteurException("Echec de l'appel a Azure OpenAI", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GenerationTourTuteurException("Appel a Azure OpenAI interrompu", e);
        }
    }

    private String construireInput(ContexteTour contexte) {
        StringBuilder input = new StringBuilder();
        if (contexte.historique().isEmpty() && contexte.derniereReponseEtudiant() == null) {
            input.append("C'est le tout premier tour sur cette notion. L'etudiant n'a encore rien dit. "
                    + "Commence la conversation.");
            return input.toString();
        }

        input.append("Historique de la conversation sur cette notion :\n");
        for (TourHistorique tour : contexte.historique()) {
            input.append("[").append(tour.locuteur()).append("] ").append(tour.texte()).append("\n");
        }
        if (contexte.derniereReponseEtudiant() != null) {
            input.append("\nDerniere reponse de l'etudiant, a evaluer maintenant : ")
                    .append(contexte.derniereReponseEtudiant());
        }
        return input.toString();
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
        throw new GenerationTourTuteurException("Reponse d'Azure OpenAI sans message exploitable : " + corpsReponse);
    }

    private TourTuteurGenere extraireTour(String contenu) {
        String nettoye = contenu.strip();
        if (nettoye.startsWith("```")) {
            nettoye = nettoye.replaceFirst("^```(json)?", "").replaceFirst("```$", "").strip();
        }

        try {
            JsonNode noeud = JSON.readTree(nettoye);
            String texteTuteur = noeud.path("texte_tuteur").asText();
            NiveauMaitrise evaluation = NiveauMaitrise.valueOf(noeud.path("evaluation_maitrise").asText());
            boolean notionMaitrisee = noeud.path("notion_maitrisee").asBoolean(false);
            return new TourTuteurGenere(texteTuteur, evaluation, notionMaitrisee);
        } catch (IOException | IllegalArgumentException e) {
            throw new GenerationTourTuteurException("Reponse d'Azure OpenAI non exploitable : " + contenu, e);
        }
    }
}
