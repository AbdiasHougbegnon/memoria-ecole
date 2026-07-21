package com.memoria.core.locuteur;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

// NON FONCTIONNEL, CONFIRME : Azure Speaker Recognition a ete retire par
// Microsoft le 30 septembre 2025 -- ce n'est pas une question d'acces
// (Limited Access) mais un service qui n'existe plus du tout. Ce client
// ne repondra jamais, quels que soient les credentials.
//
// PLUS UN BEAN SPRING (pas de @Component) : remplace par
// IdentificateurLocuteurSpeechBrain (auto-heberge, gratuit, voir ce fichier)
// suite a la decision de ne pas payer un fournisseur cloud pour cette
// fonctionnalite. Garde dans le code comme reference de forme (meme reflexe
// que TranscripteurAzureSpeech : cle/region vides -> log + degradation,
// jamais de crash au demarrage) et comme trace de la decouverte du retrait
// du service -- pas pour etre reactive telle quelle.
//
// Les chemins et formats de requete/reponse ci-dessous restent tels
// qu'ecrits a partir de la forme generale de l'ancienne API (avant son
// retrait) -- desormais purement documentaires.
public class IdentificateurLocuteurAzureSpeech implements IdentificateurLocuteurPort {

    private static final Logger LOG = LoggerFactory.getLogger(IdentificateurLocuteurAzureSpeech.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String cle;
    private final String region;
    private final String langue;
    private final boolean configure;

    public IdentificateurLocuteurAzureSpeech(
            @Value("${azure.speech.key}") String cle,
            @Value("${azure.speech.region}") String region,
            @Value("${azure.speech.langue:fr-FR}") String langue
    ) {
        this.cle = cle;
        this.region = region;
        this.langue = langue;
        this.configure = cle != null && !cle.isBlank() && region != null && !region.isBlank();

        if (!configure) {
            LOG.warn(
                    "azure.speech.key ou azure.speech.region est vide : l'enrolement vocal echouera et "
                            + "l'identification de locuteur ne renverra jamais de correspondance tant que "
                            + "AZURE_SPEECH_KEY / AZURE_SPEECH_REGION ne sont pas definies."
            );
        }
    }

    @Override
    public String enroller(byte[] audioConsentement) {
        if (!configure) {
            throw new IdentificationLocuteurException("Credentials Azure Speech non configures, enrolement impossible");
        }

        String profilId = creerProfil();
        ajouterEnrolement(profilId, audioConsentement);
        return profilId;
    }

    @Override
    public void supprimerProfil(String profilExterneId) {
        if (!configure) {
            throw new IdentificationLocuteurException("Credentials Azure Speech non configures, suppression impossible");
        }

        URI uri = URI.create(baseUrl() + "/profiles/" + profilExterneId);
        HttpRequest requete = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Ocp-Apim-Subscription-Key", cle)
                .DELETE()
                .build();
        envoyer(requete, "suppression du profil " + profilExterneId);
    }

    @Override
    public ResultatIdentification identifier(byte[] audioSegment, List<String> profilsExternesCandidats) {
        if (!configure || profilsExternesCandidats.isEmpty()) {
            return ResultatIdentification.aucunMatch();
        }

        URI uri = URI.create(baseUrl() + "/identifySingleSpeaker?profileIds=" + String.join(",", profilsExternesCandidats));
        HttpRequest requete = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Ocp-Apim-Subscription-Key", cle)
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(audioSegment))
                .build();

        HttpResponse<String> reponse = envoyer(requete, "identification de locuteur");
        return extraireResultatIdentification(reponse.body());
    }

    private String creerProfil() {
        URI uri = URI.create(baseUrl() + "/profiles");
        HttpRequest requete = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Ocp-Apim-Subscription-Key", cle)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"locale\":\"" + langue + "\"}", StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> reponse = envoyer(requete, "creation du profil vocal");
        try {
            return JSON.readTree(reponse.body()).path("profileId").asText();
        } catch (IOException e) {
            throw new IdentificationLocuteurException("Reponse Azure Speaker Recognition illisible (creation profil)", e);
        }
    }

    private void ajouterEnrolement(String profilId, byte[] audio) {
        URI uri = URI.create(baseUrl() + "/profiles/" + profilId + "/enrollments");
        HttpRequest requete = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(60))
                .header("Ocp-Apim-Subscription-Key", cle)
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(audio))
                .build();
        envoyer(requete, "enrolement du profil " + profilId);
    }

    private HttpResponse<String> envoyer(HttpRequest requete, String description) {
        try {
            HttpResponse<String> reponse = httpClient.send(requete, HttpResponse.BodyHandlers.ofString());
            if (reponse.statusCode() / 100 != 2) {
                throw new IdentificationLocuteurException(
                        "Azure Speaker Recognition a repondu avec le statut " + reponse.statusCode()
                                + " (" + description + ") : " + reponse.body()
                );
            }
            return reponse;
        } catch (IOException e) {
            throw new IdentificationLocuteurException("Echec de l'appel a Azure Speaker Recognition (" + description + ")", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IdentificationLocuteurException("Appel a Azure Speaker Recognition interrompu (" + description + ")", e);
        }
    }

    private ResultatIdentification extraireResultatIdentification(String corpsReponse) {
        try {
            JsonNode racine = JSON.readTree(corpsReponse);
            JsonNode identifie = racine.path("identifiedProfile");
            if (identifie.isMissingNode() || identifie.path("profileId").isMissingNode()) {
                return ResultatIdentification.aucunMatch();
            }
            return new ResultatIdentification(identifie.path("profileId").asText(), identifie.path("score").asDouble(0.0));
        } catch (IOException e) {
            throw new IdentificationLocuteurException("Reponse Azure Speaker Recognition illisible (identification)", e);
        }
    }

    private String baseUrl() {
        return "https://" + region + ".api.cognitive.microsoft.com/speaker/identification/v2.0/text-independent";
    }
}
