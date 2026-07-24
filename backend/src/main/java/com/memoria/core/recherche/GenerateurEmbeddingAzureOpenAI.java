package com.memoria.core.recherche;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.memoria.core.cout.CoutAzureService;
import com.memoria.core.cout.ServiceAzure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class GenerateurEmbeddingAzureOpenAI implements GenerateurEmbeddingPort {

    private static final Logger LOG = LoggerFactory.getLogger(GenerateurEmbeddingAzureOpenAI.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String API_VERSION = "2023-05-15";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String endpointBase;
    private final String cle;
    private final String deploiement;
    private final CoutAzureService coutAzureService;
    private final double eurosPar1000Tokens;

    // Cache LRU en memoire, borne : "mise en cache des embeddings (ne jamais
    // recalculer un embedding deja connu)" -- brique "Maitrise des couts
    // Azure". Cle = texte normalise (trim + minuscules), couvre tout
    // appelant de ce port (recherche repetee, reindexation...), pas
    // seulement le cas qui a motive cette brique (RechercheService.rechercher).
    // Pas de TTL : un embedding pour un texte donne est stable sauf
    // changement de version de modele cote Azure -- risque faible, assume.
    private final Map<String, float[]> cacheEmbeddings;

    public GenerateurEmbeddingAzureOpenAI(
            @Value("${azure.openai.endpoint}") String endpointResponses,
            @Value("${azure.openai.key}") String cle,
            @Value("${azure.openai.embedding.deployment}") String deploiement,
            @Value("${memoria.cache.embeddings.taille-max:500}") int tailleMaxCache,
            @Value("${memoria.cout.azure.openai.euros-par-1k-tokens:0.002}") double eurosPar1000Tokens,
            CoutAzureService coutAzureService
    ) {
        // La ressource Azure OpenAI expose la Responses API a une URL du type
        // https://{ressource}.services.ai.azure.com/openai/v1/responses ; les
        // embeddings utilisent une route differente sur la meme ressource
        // (/openai/deployments/{deploiement}/embeddings), d'ou l'extraction de
        // la base avant "/openai/" plutot qu'une propriete d'endpoint dupliquee.
        int indexOpenai = endpointResponses == null ? -1 : endpointResponses.indexOf("/openai/");
        this.endpointBase = indexOpenai >= 0 ? endpointResponses.substring(0, indexOpenai) : endpointResponses;
        this.cle = cle;
        this.deploiement = deploiement;
        this.coutAzureService = coutAzureService;
        this.eurosPar1000Tokens = eurosPar1000Tokens;
        this.cacheEmbeddings = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, float[]> plusAncien) {
                return size() > tailleMaxCache;
            }
        });

        if (endpointBase == null || endpointBase.isBlank() || cle == null || cle.isBlank()
                || deploiement == null || deploiement.isBlank()) {
            LOG.warn(
                    "azure.openai.endpoint, azure.openai.key ou azure.openai.embedding.deployment est vide : "
                            + "la generation d'embeddings echouera tant que AZURE_OPENAI_ENDPOINT / "
                            + "AZURE_OPENAI_KEY / AZURE_OPENAI_EMBEDDING_DEPLOYMENT ne sont pas definies dans "
                            + "l'environnement qui lance l'application."
            );
        }
    }

    @Override
    public List<float[]> genererEmbeddings(List<String> textes) {
        if (textes.isEmpty()) {
            return List.of();
        }

        List<float[]> resultats = new ArrayList<>(Collections.nCopies(textes.size(), null));
        List<Integer> indicesManquants = new ArrayList<>();
        List<String> textesManquants = new ArrayList<>();

        for (int i = 0; i < textes.size(); i++) {
            float[] enCache = cacheEmbeddings.get(normaliser(textes.get(i)));
            if (enCache != null) {
                resultats.set(i, enCache);
            } else {
                indicesManquants.add(i);
                textesManquants.add(textes.get(i));
            }
        }

        if (!textesManquants.isEmpty()) {
            List<float[]> nouveauxVecteurs = appellerAzure(textesManquants);
            for (int j = 0; j < indicesManquants.size(); j++) {
                float[] vecteur = nouveauxVecteurs.get(j);
                resultats.set(indicesManquants.get(j), vecteur);
                cacheEmbeddings.put(normaliser(textesManquants.get(j)), vecteur);
            }
        }

        return resultats;
    }

    private String normaliser(String texte) {
        return texte.trim().toLowerCase(Locale.ROOT);
    }

    private List<float[]> appellerAzure(List<String> textes) {
        var corpsRequete = JSON.createObjectNode();
        ArrayNode input = corpsRequete.putArray("input");
        textes.forEach(input::add);

        try {
            String url = endpointBase + "/openai/deployments/"
                    + URLEncoder.encode(deploiement, StandardCharsets.UTF_8)
                    + "/embeddings?api-version=" + API_VERSION;

            HttpRequest requete = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("api-key", cle)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(corpsRequete)))
                    .build();

            HttpResponse<String> reponse = httpClient.send(requete, HttpResponse.BodyHandlers.ofString());
            if (reponse.statusCode() != 200) {
                throw new GenerationEmbeddingException(
                        "Azure OpenAI a repondu avec le statut " + reponse.statusCode() + " : " + reponse.body()
                );
            }

            JsonNode corpsReponse = JSON.readTree(reponse.body());
            enregistrerCout(corpsReponse);
            return extraireVecteurs(corpsReponse);
        } catch (IOException e) {
            throw new GenerationEmbeddingException("Echec de l'appel a Azure OpenAI (embeddings)", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GenerationEmbeddingException("Appel a Azure OpenAI (embeddings) interrompu", e);
        }
    }

    // Cout base sur les tokens reels renvoyes par Azure (champ "usage") quand
    // present ; repli sur un forfait si le champ est absent/inattendu -- ne
    // fait jamais planter l'appel principal pour un souci de mesure de cout.
    private void enregistrerCout(JsonNode corpsReponse) {
        int tokens = corpsReponse.path("usage").path("total_tokens").asInt(-1);
        double coutEuros = tokens >= 0
                ? (tokens / 1000.0) * eurosPar1000Tokens
                : coutAzureService.coutForfaitaireEuros();
        coutAzureService.enregistrerAppel(ServiceAzure.OPENAI_EMBEDDING, coutEuros);
    }

    private List<float[]> extraireVecteurs(JsonNode corpsReponse) {
        List<JsonNode> elements = new ArrayList<>();
        corpsReponse.path("data").forEach(elements::add);
        // L'API renvoie les vecteurs avec un champ "index" : on trie pour
        // garantir le meme ordre que la liste de textes en entree, meme si
        // le fournisseur ne garantit pas l'ordre de retour.
        elements.sort((a, b) -> Integer.compare(a.path("index").asInt(), b.path("index").asInt()));

        List<float[]> vecteurs = new ArrayList<>();
        for (JsonNode element : elements) {
            JsonNode embedding = element.path("embedding");
            float[] vecteur = new float[embedding.size()];
            for (int i = 0; i < vecteur.length; i++) {
                vecteur[i] = (float) embedding.get(i).asDouble();
            }
            vecteurs.add(vecteur);
        }
        if (vecteurs.size() != elements.size()) {
            throw new GenerationEmbeddingException("Reponse d'Azure OpenAI incomplete : " + corpsReponse);
        }
        return vecteurs;
    }
}
