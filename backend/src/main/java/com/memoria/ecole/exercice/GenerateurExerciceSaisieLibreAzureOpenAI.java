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

// Meme ressource Azure OpenAI ("Responses API") que GenerateurQcmAzureOpenAI/
// GenerateurResumeCoursAzureOpenAI, avec deux schemas JSON differents (l'un
// pour generer des questions ouvertes, l'autre pour evaluer une reponse).
@Component
public class GenerateurExerciceSaisieLibreAzureOpenAI implements GenerateurExerciceSaisieLibrePort {

    private static final Logger LOG = LoggerFactory.getLogger(GenerateurExerciceSaisieLibreAzureOpenAI.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String CONSIGNE_GENERATION = """
            Tu es un assistant pedagogique qui construit des exercices de revision a reponse
            libre (pas de QCM) a partir du contenu de plusieurs cours et documents d'une matiere,
            en francais. Genere exactement 3 questions ouvertes qui demandent une reponse redigee,
            pas un choix parmi des propositions.
            Reponds UNIQUEMENT avec un objet JSON valide de la forme exacte :
            {
              "questions": [
                {
                  "enonce": "l'enonce de la question ouverte",
                  "elements_attendus": "les points cles qu'une bonne reponse doit couvrir"
                }
              ]
            }
            Ne porte que sur des notions reellement presentes dans le contenu fourni, n'invente rien.
            Aucun texte avant ou apres le JSON, aucun bloc de code markdown.
            """;

    private static final String CONSIGNE_EVALUATION = """
            Tu es un assistant pedagogique qui evalue la reponse d'un etudiant a une question
            ouverte, en te basant sur les elements de reponse attendus, en francais.
            Reponds UNIQUEMENT avec un objet JSON valide de la forme exacte :
            {
              "niveau": "NON_ABORDEE" | "EN_COURS" | "MAITRISEE",
              "retour": "un retour bref et constructif pour l'etudiant, en s'appuyant sur ce qu'il a ecrit"
            }
            "NON_ABORDEE" si la reponse est vide, hors-sujet ou ne montre aucune comprehension.
            "EN_COURS" si la reponse montre une comprehension partielle ou incomplete.
            "MAITRISEE" si la reponse couvre correctement les elements attendus.
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

    public GenerateurExerciceSaisieLibreAzureOpenAI(
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
                            + "la generation d'exercices echouera tant que AZURE_OPENAI_ENDPOINT / "
                            + "AZURE_OPENAI_KEY / AZURE_OPENAI_DEPLOYMENT ne sont pas definies dans "
                            + "l'environnement qui lance l'application."
            );
        }
    }

    @Override
    public ExercicesGeneres genererExercices(String contenuMatiere) {
        String contenu = appelerAzureOpenAI(CONSIGNE_GENERATION, contenuMatiere);
        return extraireExercices(contenu);
    }

    @Override
    public EvaluationReponseLibre evaluerReponse(String enonce, String elementsAttendus, String reponseEtudiant) {
        String entree = "Question : " + enonce
                + "\nElements de reponse attendus : " + elementsAttendus
                + "\nReponse de l'etudiant : " + reponseEtudiant;
        String contenu = appelerAzureOpenAI(CONSIGNE_EVALUATION, entree);
        return extraireEvaluation(contenu);
    }

    private String appelerAzureOpenAI(String consigne, String entree) {
        var corpsRequete = JSON.createObjectNode();
        corpsRequete.put("model", modele);
        corpsRequete.put("instructions", consigne);
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
            return extraireTexteDeSortie(corpsReponse);
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

    private ExercicesGeneres extraireExercices(String contenu) {
        try {
            JsonNode noeud = JSON.readTree(nettoyer(contenu));
            List<QuestionSaisieLibreExtraite> questions = new ArrayList<>();
            noeud.path("questions").forEach(question -> questions.add(new QuestionSaisieLibreExtraite(
                    question.path("enonce").asText(),
                    question.path("elements_attendus").asText()
            )));
            return new ExercicesGeneres(questions);
        } catch (IOException e) {
            throw new GenerationExerciceException("Reponse d'Azure OpenAI non exploitable : " + contenu, e);
        }
    }

    private EvaluationReponseLibre extraireEvaluation(String contenu) {
        try {
            JsonNode noeud = JSON.readTree(nettoyer(contenu));
            NiveauMaitrise niveau = NiveauMaitrise.valueOf(noeud.path("niveau").asText());
            return new EvaluationReponseLibre(niveau, noeud.path("retour").asText());
        } catch (IOException | IllegalArgumentException e) {
            throw new GenerationExerciceException("Reponse d'Azure OpenAI non exploitable : " + contenu, e);
        }
    }
}
