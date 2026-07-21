package com.memoria.entreprise.compterendu;

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
import java.util.ArrayList;
import java.util.List;

@Component
public class GenerateurCompteRenduAzureOpenAI implements GenerateurCompteRenduPort {

    private static final Logger LOG = LoggerFactory.getLogger(GenerateurCompteRenduAzureOpenAI.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String CONSIGNE = """
            Tu es un assistant qui redige un compte rendu de reunion ou de cours complet et fidele, en francais.
            Chaque ligne de la transcription commence par une etiquette suivie de " : " indiquant qui parle --
            soit un vrai nom (ex: "Claire Dubois : ..."), soit un repere generique ("Intervenant A : ...").
            Pour designer un responsable, reprends EXACTEMENT l'etiquette telle qu'elle apparait (le nom ou le
            repere generique), caractere pour caractere. N'invente jamais un nom qui n'apparait pas comme
            etiquette, meme s'il est mentionne ailleurs dans le contenu de la conversation.
            Reponds UNIQUEMENT avec un objet JSON valide de la forme exacte :
            {
              "synthese": "un paragraphe de synthese fidele au contenu",
              "decisions": ["decision actee 1", "decision actee 2"],
              "actions": [
                {"description": "action concrete a l'imperatif", "responsable": "l'etiquette exacte ou null", "echeance": "delai mentionne ou null"}
              ]
            }
            Si aucune decision ou action concrete n'est mentionnee, renvoie un tableau vide pour le champ
            correspondant. Le champ "responsable" et le champ "echeance" doivent valoir JSON null (pas la
            chaine "null") quand l'information n'est pas mentionnee dans la transcription.
            Aucun texte avant ou apres le JSON, aucun bloc de code markdown.
            """;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String endpoint;
    private final String cle;
    private final String modele;

    public GenerateurCompteRenduAzureOpenAI(
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
                            + "la generation de compte rendu echouera tant que AZURE_OPENAI_ENDPOINT / "
                            + "AZURE_OPENAI_KEY / AZURE_OPENAI_DEPLOYMENT ne sont pas definies dans "
                            + "l'environnement qui lance l'application."
            );
        }
    }

    @Override
    public CompteRenduGenere genererCompteRendu(String transcriptComplet) {
        // Meme ressource Azure OpenAI ("Responses API") que GenerateurResumeAzureOpenAI,
        // avec un schema JSON different. Voir ce dernier pour le contexte sur
        // pourquoi cette API precisement (pas Chat Completions, qui 404 sur cette
        // ressource AI Foundry).
        var corpsRequete = JSON.createObjectNode();
        corpsRequete.put("model", modele);
        corpsRequete.put("instructions", CONSIGNE);
        corpsRequete.put("input", transcriptComplet);

        try {
            // Delai genereux : la sortie structuree (synthese + decisions + actions)
            // est plus longue a generer qu'un resume simple, et une vraie reunion
            // produit une transcription bien plus longue que nos tests -- verifie
            // empiriquement : 60s ne suffisaient pas sur un cours de plusieurs
            // minutes.
            HttpRequest requete = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(120))
                    .header("api-key", cle)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(corpsRequete)))
                    .build();

            HttpResponse<String> reponse = httpClient.send(requete, HttpResponse.BodyHandlers.ofString());
            if (reponse.statusCode() != 200) {
                throw new GenerationCompteRenduException(
                        "Azure OpenAI a repondu avec le statut " + reponse.statusCode() + " : " + reponse.body()
                );
            }

            JsonNode corpsReponse = JSON.readTree(reponse.body());
            String contenu = extraireTexteDeSortie(corpsReponse);
            return extraireCompteRendu(contenu);
        } catch (IOException e) {
            throw new GenerationCompteRenduException("Echec de l'appel a Azure OpenAI", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GenerationCompteRenduException("Appel a Azure OpenAI interrompu", e);
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
        throw new GenerationCompteRenduException("Reponse d'Azure OpenAI sans message exploitable : " + corpsReponse);
    }

    private CompteRenduGenere extraireCompteRendu(String contenu) {
        String nettoye = contenu.strip();
        if (nettoye.startsWith("```")) {
            nettoye = nettoye.replaceFirst("^```(json)?", "").replaceFirst("```$", "").strip();
        }

        try {
            JsonNode noeud = JSON.readTree(nettoye);
            String synthese = noeud.path("synthese").asText();

            List<String> decisions = new ArrayList<>();
            noeud.path("decisions").forEach(decision -> decisions.add(decision.asText()));

            List<ActionExtraite> actions = new ArrayList<>();
            noeud.path("actions").forEach(action -> actions.add(new ActionExtraite(
                    action.path("description").asText(),
                    action.path("responsable").isNull() ? null : action.path("responsable").asText(null),
                    action.path("echeance").isNull() ? null : action.path("echeance").asText(null)
            )));

            return new CompteRenduGenere(synthese, decisions, actions);
        } catch (IOException e) {
            throw new GenerationCompteRenduException("Reponse d'Azure OpenAI non exploitable : " + contenu, e);
        }
    }
}
