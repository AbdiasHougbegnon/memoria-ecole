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
import java.util.ArrayList;
import java.util.List;

// Meme ressource Azure OpenAI ("Responses API") que
// GenerateurExerciceSaisieLibreAzureOpenAI/GenerateurQcmAzureOpenAI, avec son
// propre schema JSON : decoupe l'enonce et la reponse (deux photos separees,
// phase 28) en exercices individuels et corrige chacun avec l'enonce reel
// comme reference -- plus fiable que deviner l'enonce a partir de la seule
// reponse (limite assumee des phases 24/26/27).
@Component
public class CorrecteurTravailPapierAzureOpenAI implements CorrecteurTravailPapierPort {

    private static final Logger LOG = LoggerFactory.getLogger(CorrecteurTravailPapierAzureOpenAI.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    // Sortie decoupee en exercices puis en points (phases 27/28) plutot qu'un
    // seul bloc de texte -- un mur de texte etait juge illisible et
    // impossible a reviser point par point cote frontend (retour utilisateur
    // direct).
    private static final String CONSIGNE = """
            Tu es un assistant pedagogique qui corrige le travail qu'un etudiant a fait sur
            papier, en francais. Tu recois le texte extrait de deux photos : celle de l'enonce
            (le sujet/les questions) et celle de la copie de l'etudiant (ses reponses), toutes
            deux pouvant contenir des imperfections d'OCR mineures a ignorer.
            Decoupe l'enonce en exercices/questions individuels, associe a chacun la portion
            correspondante de la reponse de l'etudiant (dans le meme ordre que l'enonce sauf
            indication contraire dans la copie), et corrige chaque exercice separement :
            decoupe la correction de chaque exercice en points distincts et courts (un point par
            erreur ou par element a ameliorer, jamais un seul paragraphe qui melange tout), et
            evalue son niveau de maitrise.
            Reponds UNIQUEMENT avec un objet JSON valide de la forme exacte :
            {
              "exercices": [
                {
                  "enonce": "l'enonce de cet exercice, tel qu'identifie dans le texte source",
                  "reponse_etudiant": "la portion de reponse de l'etudiant correspondant a cet exercice",
                  "niveau": "NON_ABORDEE" | "EN_COURS" | "MAITRISEE",
                  "synthese_correction": "1 a 2 phrases resumant l'evaluation de cet exercice",
                  "points": [
                    {
                      "sujet": "titre court du point (3-6 mots, ex. 'Date du calcul des probabilites')",
                      "constat": "ce que l'etudiant a ecrit et pourquoi c'est incomplet ou incorrect (2-3 phrases)",
                      "correction_attendue": "la reponse ou methode correcte attendue, explicite et concrete (2-4 phrases)"
                    }
                  ]
                }
              ]
            }
            Si un exercice est deja correct, tu peux quand meme creer un point qui le confirme
            brievement plutot que de l'omettre. Genere entre 1 et 8 points par exercice selon la
            richesse reelle du travail -- jamais un nombre fixe. Si l'enonce ne semble contenir
            qu'un seul exercice, renvoie un seul element dans "exercices".
            "NON_ABORDEE" si la reponse a cet exercice est vide, illisible ou ne montre aucune
            tentative pertinente. "EN_COURS" si elle est partiellement correcte ou incomplete.
            "MAITRISEE" si elle est correcte dans son ensemble.
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
    public List<ExerciceCorrige> corriger(String texteEnonce, String texteReponse) {
        String entree = "ENONCE :\n" + texteEnonce + "\n\nREPONSE DE L'ETUDIANT :\n" + texteReponse;

        var corpsRequete = JSON.createObjectNode();
        corpsRequete.put("model", modele);
        corpsRequete.put("instructions", CONSIGNE);
        corpsRequete.put("input", entree);

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

    private List<ExerciceCorrige> extraireCorrection(String contenu) {
        try {
            JsonNode noeud = JSON.readTree(nettoyer(contenu));
            List<ExerciceCorrige> exercices = new ArrayList<>();
            noeud.path("exercices").forEach(exerciceNode -> {
                NiveauMaitrise niveau = NiveauMaitrise.valueOf(exerciceNode.path("niveau").asText());
                List<PointCorrection> points = new ArrayList<>();
                exerciceNode.path("points").forEach(point -> points.add(new PointCorrection(
                        point.path("sujet").asText(),
                        point.path("constat").asText(),
                        point.path("correction_attendue").asText()
                )));
                exercices.add(new ExerciceCorrige(
                        exerciceNode.path("enonce").asText(),
                        exerciceNode.path("reponse_etudiant").asText(),
                        niveau,
                        exerciceNode.path("synthese_correction").asText(),
                        points
                ));
            });
            return exercices;
        } catch (IOException | IllegalArgumentException e) {
            throw new GenerationExerciceException("Reponse d'Azure OpenAI non exploitable : " + contenu, e);
        }
    }
}
