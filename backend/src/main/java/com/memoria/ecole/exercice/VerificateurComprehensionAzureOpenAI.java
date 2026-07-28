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
import java.util.stream.Collectors;

// Meme ressource Azure OpenAI ("Responses API") que les autres generateurs du
// package -- deux operations : generer une question de verification de
// comprehension (choix a cocher) a partir d'une correction deja donnee, et
// evaluer qualitativement une reponse redigee librement a cette question.
@Component
public class VerificateurComprehensionAzureOpenAI implements VerificateurComprehensionPort {

    private static final Logger LOG = LoggerFactory.getLogger(VerificateurComprehensionAzureOpenAI.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String CONSIGNE_GENERATION = """
            Tu es un assistant pedagogique. Un etudiant a fait un exercice sur papier et a deja
            recu une correction detaillee. Ta tache : construire UNE question de verification de
            comprehension a choix multiple, qui permette de confirmer que l'etudiant a bien
            compris la correction (pas de repeter l'exercice, mais de verifier la compréhension
            du point corrige). Plusieurs reponses peuvent etre correctes.
            Reponds UNIQUEMENT avec un objet JSON valide de la forme exacte :
            {
              "enonce": "la question de verification",
              "choix": [
                { "texte": "un choix propose", "correct": true ou false }
              ]
            }
            Propose entre 3 et 5 choix, dont au moins un correct. En francais.
            Aucun texte avant ou apres le JSON, aucun bloc de code markdown.
            """;

    private static final String CONSIGNE_EVALUATION = """
            Tu es un assistant pedagogique qui evalue la reponse libre (tapee ou dictee a
            l'oral puis transcrite) d'un etudiant a une question de verification de
            comprehension, en francais.
            Reponds UNIQUEMENT avec un objet JSON valide de la forme exacte :
            {
              "niveau": "NON_ABORDEE" | "EN_COURS" | "MAITRISEE"
            }
            "NON_ABORDEE" si la reponse est vide, hors-sujet ou ne montre aucune comprehension.
            "EN_COURS" si la reponse montre une comprehension partielle.
            "MAITRISEE" si la reponse montre une comprehension correcte du point verifie.
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

    public VerificateurComprehensionAzureOpenAI(
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
                            + "la verification de comprehension echouera tant que AZURE_OPENAI_ENDPOINT / "
                            + "AZURE_OPENAI_KEY / AZURE_OPENAI_DEPLOYMENT ne sont pas definies dans "
                            + "l'environnement qui lance l'application."
            );
        }
    }

    @Override
    public QuestionVerificationGeneree genererQuestion(String enonce, String correctionSynthese, List<PointCorrection> points) {
        String pointsTexte = points.stream()
                .map(point -> "- " + point.getSujet() + " : " + point.getConstat() + " " + point.getCorrectionAttendue())
                .collect(Collectors.joining("\n"));
        String entree = "Enonce de l'exercice : " + enonce
                + "\nSynthese de la correction : " + correctionSynthese
                + "\nPoints de correction :\n" + pointsTexte;

        String contenu = appelerAzureOpenAI(CONSIGNE_GENERATION, entree);
        return extraireQuestion(contenu);
    }

    @Override
    public NiveauMaitrise evaluerReponseLibre(String questionVerification, String reponseEtudiant) {
        String entree = "Question de verification : " + questionVerification + "\nReponse de l'etudiant : " + reponseEtudiant;
        String contenu = appelerAzureOpenAI(CONSIGNE_EVALUATION, entree);
        return extraireNiveau(contenu);
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

    private QuestionVerificationGeneree extraireQuestion(String contenu) {
        try {
            JsonNode noeud = JSON.readTree(nettoyer(contenu));
            List<ChoixVerification> choix = new ArrayList<>();
            noeud.path("choix").forEach(c -> choix.add(new ChoixVerification(c.path("texte").asText(), c.path("correct").asBoolean())));
            return new QuestionVerificationGeneree(noeud.path("enonce").asText(), choix);
        } catch (IOException e) {
            throw new GenerationExerciceException("Reponse d'Azure OpenAI non exploitable : " + contenu, e);
        }
    }

    private NiveauMaitrise extraireNiveau(String contenu) {
        try {
            JsonNode noeud = JSON.readTree(nettoyer(contenu));
            return NiveauMaitrise.valueOf(noeud.path("niveau").asText());
        } catch (IOException | IllegalArgumentException e) {
            throw new GenerationExerciceException("Reponse d'Azure OpenAI non exploitable : " + contenu, e);
        }
    }
}
