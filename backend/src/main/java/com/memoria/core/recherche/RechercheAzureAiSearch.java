package com.memoria.core.recherche;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class RechercheAzureAiSearch implements RecherchePort {

    private static final Logger LOG = LoggerFactory.getLogger(RechercheAzureAiSearch.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String API_VERSION = "2024-07-01";

    // Dimension propre au modele d'embeddings deploye (text-embedding-ada-002).
    // A changer conjointement si le modele d'embeddings change un jour.
    private static final int DIMENSIONS_VECTEUR = 1536;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String endpoint;
    private final String cle;
    private final String index;

    public RechercheAzureAiSearch(
            @Value("${azure.search.endpoint}") String endpoint,
            @Value("${azure.search.key}") String cle,
            @Value("${azure.search.index}") String index
    ) {
        this.endpoint = endpoint;
        this.cle = cle;
        this.index = index;

        if (endpoint == null || endpoint.isBlank() || cle == null || cle.isBlank()) {
            LOG.warn(
                    "azure.search.endpoint ou azure.search.key est vide : l'indexation et la recherche "
                            + "semantique echoueront tant que AZURE_SEARCH_ENDPOINT / AZURE_SEARCH_KEY ne "
                            + "sont pas definies dans l'environnement qui lance l'application."
            );
        }
    }

    @PostConstruct
    void assurerIndexExiste() {
        if (endpoint == null || endpoint.isBlank() || cle == null || cle.isBlank()) {
            return;
        }

        ObjectNode schema = JSON.createObjectNode();
        schema.put("name", index);
        ArrayNode fields = schema.putArray("fields");
        fields.add(champ("id", "Edm.String", true, true, false, false));
        fields.add(champ("sessionId", "Edm.String", false, true, false, false));
        fields.add(champ("titreSession", "Edm.String", false, true, true, false));
        fields.add(champ("texte", "Edm.String", false, false, true, false));
        fields.add(champ("locuteur", "Edm.Int32", false, true, false, false));
        fields.add(champ("numeroSequence", "Edm.Int32", false, true, false, false));
        fields.add(champ("offsetMillisecondes", "Edm.Int64", false, true, false, true));
        fields.add(champ("dureeMillisecondes", "Edm.Int64", false, true, false, false));
        fields.add(champ("dateSession", "Edm.DateTimeOffset", false, true, false, true));

        ObjectNode champVecteur = JSON.createObjectNode();
        champVecteur.put("name", "vecteur");
        champVecteur.put("type", "Collection(Edm.Single)");
        champVecteur.put("searchable", true);
        champVecteur.put("dimensions", DIMENSIONS_VECTEUR);
        champVecteur.put("vectorSearchProfile", "profil-defaut");
        fields.add(champVecteur);

        ObjectNode vectorSearch = schema.putObject("vectorSearch");
        ArrayNode algorithms = vectorSearch.putArray("algorithms");
        ObjectNode algo = JSON.createObjectNode();
        algo.put("name", "hnsw-defaut");
        algo.put("kind", "hnsw");
        algorithms.add(algo);
        ArrayNode profiles = vectorSearch.putArray("profiles");
        ObjectNode profil = JSON.createObjectNode();
        profil.put("name", "profil-defaut");
        profil.put("algorithm", "hnsw-defaut");
        profiles.add(profil);

        try {
            HttpRequest requete = HttpRequest.newBuilder(URI.create(endpoint + "/indexes/" + index + "?api-version=" + API_VERSION))
                    .timeout(Duration.ofSeconds(30))
                    .header("api-key", cle)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(schema)))
                    .build();
            HttpResponse<String> reponse = httpClient.send(requete, HttpResponse.BodyHandlers.ofString());
            if (reponse.statusCode() >= 200 && reponse.statusCode() < 300) {
                LOG.info("Index Azure AI Search '{}' pret (statut {})", index, reponse.statusCode());
            } else {
                LOG.warn("Impossible de creer/mettre a jour l'index Azure AI Search '{}' : statut {} - {}",
                        index, reponse.statusCode(), reponse.body());
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.warn("Echec de la creation/mise a jour de l'index Azure AI Search '{}' au demarrage", index, e);
        }
    }

    private ObjectNode champ(String nom, String type, boolean cle, boolean filterable, boolean searchable, boolean sortable) {
        ObjectNode champ = JSON.createObjectNode();
        champ.put("name", nom);
        champ.put("type", type);
        champ.put("key", cle);
        champ.put("filterable", filterable);
        champ.put("searchable", searchable);
        champ.put("sortable", sortable);
        champ.put("retrievable", true);
        return champ;
    }

    @Override
    public void indexerSegments(
            UUID sessionId,
            String titreSession,
            Instant dateSession,
            List<SegmentARecherche> segments,
            List<float[]> vecteurs
    ) {
        if (segments.isEmpty()) {
            return;
        }
        if (segments.size() != vecteurs.size()) {
            throw new RechercheException("Le nombre de segments (" + segments.size()
                    + ") ne correspond pas au nombre de vecteurs (" + vecteurs.size() + ")");
        }

        ObjectNode corps = JSON.createObjectNode();
        ArrayNode value = corps.putArray("value");
        for (int i = 0; i < segments.size(); i++) {
            SegmentARecherche segment = segments.get(i);
            ObjectNode doc = JSON.createObjectNode();
            doc.put("@search.action", "mergeOrUpload");
            doc.put("id", sessionId + "_" + segment.numeroSequence() + "_" + segment.offsetMillisecondes());
            doc.put("sessionId", sessionId.toString());
            doc.put("titreSession", titreSession);
            doc.put("texte", segment.texte());
            doc.put("locuteur", segment.locuteur());
            doc.put("numeroSequence", segment.numeroSequence());
            doc.put("offsetMillisecondes", segment.offsetMillisecondes());
            doc.put("dureeMillisecondes", segment.dureeMillisecondes());
            doc.put("dateSession", dateSession.toString());
            ArrayNode vecteur = doc.putArray("vecteur");
            for (float composante : vecteurs.get(i)) {
                vecteur.add(composante);
            }
            value.add(doc);
        }

        try {
            HttpRequest requete = HttpRequest.newBuilder(
                            URI.create(endpoint + "/indexes/" + index + "/docs/index?api-version=" + API_VERSION))
                    .timeout(Duration.ofSeconds(30))
                    .header("api-key", cle)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(corps)))
                    .build();
            HttpResponse<String> reponse = httpClient.send(requete, HttpResponse.BodyHandlers.ofString());
            if (reponse.statusCode() != 200 && reponse.statusCode() != 201) {
                throw new RechercheException(
                        "Azure AI Search a repondu avec le statut " + reponse.statusCode() + " : " + reponse.body()
                );
            }
        } catch (IOException e) {
            throw new RechercheException("Echec de l'indexation dans Azure AI Search", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RechercheException("Indexation dans Azure AI Search interrompue", e);
        }
    }

    @Override
    public List<ResultatRecherche> rechercher(String texteRequete, float[] vecteurRequete, int limite) {
        ObjectNode corps = JSON.createObjectNode();
        ArrayNode vectorQueries = corps.putArray("vectorQueries");
        ObjectNode vectorQuery = JSON.createObjectNode();
        ArrayNode vecteur = vectorQuery.putArray("vector");
        for (float composante : vecteurRequete) {
            vecteur.add(composante);
        }
        vectorQuery.put("k", limite);
        vectorQuery.put("fields", "vecteur");
        vectorQuery.put("kind", "vector");
        vectorQueries.add(vectorQuery);
        // Recherche hybride (vecteur + mots-cles BM25 sur "texte", fusionnes par
        // Azure via RRF) : verifie empiriquement que le vecteur seul donne des
        // scores cosinus trop resserres avec ada-002 (~0.82 pour du bruit total
        // contre ~0.83-0.89 pour un vrai match) pour distinguer la pertinence --
        // l'hybride ecarte nettement mieux les deux (ex. 0.033 vs 0.017 sur le
        // meme cas de test), sans cout ni reindexation supplementaires.
        corps.put("search", texteRequete);
        corps.put("queryType", "simple");
        corps.put("select", "sessionId,titreSession,dateSession,texte,locuteur,offsetMillisecondes,dureeMillisecondes,numeroSequence");
        corps.put("top", limite);

        try {
            HttpRequest requete = HttpRequest.newBuilder(
                            URI.create(endpoint + "/indexes/" + index + "/docs/search?api-version=" + API_VERSION))
                    .timeout(Duration.ofSeconds(30))
                    .header("api-key", cle)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(corps)))
                    .build();
            HttpResponse<String> reponse = httpClient.send(requete, HttpResponse.BodyHandlers.ofString());
            if (reponse.statusCode() != 200) {
                throw new RechercheException(
                        "Azure AI Search a repondu avec le statut " + reponse.statusCode() + " : " + reponse.body()
                );
            }
            return extraireResultats(JSON.readTree(reponse.body()));
        } catch (IOException e) {
            throw new RechercheException("Echec de la recherche dans Azure AI Search", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RechercheException("Recherche dans Azure AI Search interrompue", e);
        }
    }

    private List<ResultatRecherche> extraireResultats(JsonNode corpsReponse) {
        List<ResultatRecherche> resultats = new ArrayList<>();
        for (JsonNode doc : corpsReponse.path("value")) {
            resultats.add(new ResultatRecherche(
                    UUID.fromString(doc.path("sessionId").asText()),
                    doc.path("titreSession").asText(),
                    Instant.parse(doc.path("dateSession").asText()),
                    doc.path("texte").asText(),
                    doc.path("locuteur").asInt(),
                    doc.path("offsetMillisecondes").asLong(),
                    doc.path("dureeMillisecondes").asLong(),
                    doc.path("numeroSequence").asInt(),
                    doc.path("@search.score").asDouble()
            ));
        }
        return resultats;
    }
}
