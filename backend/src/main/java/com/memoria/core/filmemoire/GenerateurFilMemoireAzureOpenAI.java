package com.memoria.core.filmemoire;

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
import java.util.List;
import java.util.UUID;

@Component
public class GenerateurFilMemoireAzureOpenAI implements GenerateurFilMemoirePort {

    private static final Logger LOG = LoggerFactory.getLogger(GenerateurFilMemoireAzureOpenAI.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String CONSIGNE = """
            Tu regroupes des sessions (reunions ou cours) en fils de discussion par theme, en francais.
            On te donne le resume d'une nouvelle session, et une liste de fils existants candidats
            (chacun avec un identifiant, un nom, et son resume cumulatif).
            Decide si cette nouvelle session appartient reellement au meme sujet qu'un de ces fils
            candidats, ou si c'est un sujet different qui merite un nouveau fil. Sois strict : ne
            rattache une session a un fil que si le sujet est vraiment le meme, pas juste vaguement
            similaire.
            Reponds UNIQUEMENT avec un objet JSON valide de la forme exacte :
            {
              "filId": "un des identifiants candidats donnes, ou JSON null si nouveau fil",
              "nouveauNom": "un nom court et clair pour le nouveau fil (3 a 6 mots), ou JSON null si filId n'est pas null",
              "resumeMisAJour": "le resume cumulatif mis a jour : si filId non null, fusionne l'ancien resume cumulatif et le nouveau contenu ; si nouveau fil, reprend le resume de la nouvelle session"
            }
            Aucun texte avant ou apres le JSON, aucun bloc de code markdown.
            """;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String endpoint;
    private final String cle;
    private final String modele;

    public GenerateurFilMemoireAzureOpenAI(
            @Value("${azure.openai.endpoint}") String endpoint,
            @Value("${azure.openai.key}") String cle,
            @Value("${azure.openai.deployment}") String modele
    ) {
        this.endpoint = endpoint;
        this.cle = cle;
        this.modele = modele;
    }

    @Override
    public DecisionFilMemoire deciderFil(String resumeSession, List<CandidatFilMemoire> candidats) {
        String entree = construireEntree(resumeSession, candidats);

        var corpsRequete = JSON.createObjectNode();
        corpsRequete.put("model", modele);
        corpsRequete.put("instructions", CONSIGNE);
        corpsRequete.put("input", entree);

        try {
            HttpRequest requete = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(60))
                    .header("api-key", cle)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(corpsRequete)))
                    .build();

            HttpResponse<String> reponse = httpClient.send(requete, HttpResponse.BodyHandlers.ofString());
            if (reponse.statusCode() != 200) {
                throw new GenerationFilMemoireException(
                        "Azure OpenAI a repondu avec le statut " + reponse.statusCode() + " : " + reponse.body()
                );
            }

            String contenu = extraireTexteDeSortie(JSON.readTree(reponse.body()));
            return extraireDecision(contenu, candidats);
        } catch (IOException e) {
            throw new GenerationFilMemoireException("Echec de l'appel a Azure OpenAI", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GenerationFilMemoireException("Appel a Azure OpenAI interrompu", e);
        }
    }

    private String construireEntree(String resumeSession, List<CandidatFilMemoire> candidats) {
        StringBuilder texte = new StringBuilder();
        texte.append("Resume de la nouvelle session :\n").append(resumeSession).append("\n\n");
        texte.append("Fils candidats :\n");
        if (candidats.isEmpty()) {
            texte.append("Aucun fil candidat.\n");
        } else {
            for (CandidatFilMemoire candidat : candidats) {
                texte.append("- id=").append(candidat.id())
                        .append(" | nom=\"").append(candidat.nom())
                        .append("\" | resume cumulatif : \"").append(candidat.resumeCumulatif()).append("\"\n");
            }
        }
        return texte.toString();
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
        throw new GenerationFilMemoireException("Reponse d'Azure OpenAI sans message exploitable : " + corpsReponse);
    }

    private DecisionFilMemoire extraireDecision(String contenu, List<CandidatFilMemoire> candidats) {
        String nettoye = contenu.strip();
        if (nettoye.startsWith("```")) {
            nettoye = nettoye.replaceFirst("^```(json)?", "").replaceFirst("```$", "").strip();
        }

        try {
            JsonNode noeud = JSON.readTree(nettoye);
            UUID filId = extraireFilIdValide(noeud.path("filId"), candidats);
            String nouveauNom = noeud.path("nouveauNom").isNull() ? null : noeud.path("nouveauNom").asText(null);
            String resumeMisAJour = noeud.path("resumeMisAJour").asText(null);
            return new DecisionFilMemoire(filId, nouveauNom, resumeMisAJour);
        } catch (IOException e) {
            throw new GenerationFilMemoireException("Reponse d'Azure OpenAI non exploitable : " + contenu, e);
        }
    }

    // Ne fait jamais confiance aveuglement a un identifiant renvoye par le
    // modele : ne retient que s'il correspond reellement a l'un des
    // candidats presentes, sinon on traite comme "nouveau fil" (comportement
    // le plus sur en cas de reponse imprevue).
    private UUID extraireFilIdValide(JsonNode noeudFilId, List<CandidatFilMemoire> candidats) {
        if (noeudFilId.isNull() || noeudFilId.asText(null) == null) {
            return null;
        }
        try {
            UUID id = UUID.fromString(noeudFilId.asText());
            boolean estUnCandidatConnu = candidats.stream().anyMatch(c -> c.id().equals(id));
            return estUnCandidatConnu ? id : null;
        } catch (IllegalArgumentException e) {
            LOG.warn("Azure OpenAI a renvoye un filId non exploitable : {}", noeudFilId.asText());
            return null;
        }
    }
}
