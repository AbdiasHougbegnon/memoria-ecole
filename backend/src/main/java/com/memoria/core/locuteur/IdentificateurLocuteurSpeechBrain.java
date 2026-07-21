package com.memoria.core.locuteur;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

// Remplacement gratuit et auto-heberge d'Azure Speaker Recognition (retire
// le 30 septembre 2025 -- voir IdentificateurLocuteurAzureSpeech, conserve
// comme reference documentaire mais non fonctionnel et non instancie). Appelle
// speaker-service/ (Python, FastAPI + SpeechBrain ECAPA-TDNN, aucun cout par
// appel, juste du calcul local) via une API HTTP minimale -- meme principe
// que les clients Azure du projet, juste un fournisseur different derriere
// le meme port IdentificateurLocuteurPort.
//
// Meme reflexe de degradation gracieuse que les clients Azure : si le
// service n'est pas joignable, on ne bloque jamais l'appelant plus que
// necessaire (enroller/supprimerProfil levent une exception geree par
// l'appelant ; identifier() renvoie aucunMatch() pour laisser
// IdentificationLocuteurService continuer les autres locuteurs).
@Component
@Profile("!verification-locuteur")
public class IdentificateurLocuteurSpeechBrain implements IdentificateurLocuteurPort {

    private static final Logger LOG = LoggerFactory.getLogger(IdentificateurLocuteurSpeechBrain.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String urlBase;

    public IdentificateurLocuteurSpeechBrain(
            @Value("${memoria.speaker-service.url:http://127.0.0.1:8090}") String urlBase) {
        this.urlBase = urlBase;
    }

    @Override
    public String enroller(byte[] audioConsentement) {
        HttpResponse<String> reponse = envoyerMultipart(urlBase + "/profils", audioConsentement, "enrolement");
        try {
            return JSON.readTree(reponse.body()).path("profilId").asText();
        } catch (IOException e) {
            throw new IdentificationLocuteurException("Reponse du speaker-service illisible (enrolement)", e);
        }
    }

    @Override
    public void supprimerProfil(String profilExterneId) {
        URI uri = URI.create(urlBase + "/profils/" + profilExterneId);
        HttpRequest requete = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .DELETE()
                .build();
        try {
            HttpResponse<String> reponse = httpClient.send(requete, HttpResponse.BodyHandlers.ofString());
            if (reponse.statusCode() / 100 != 2) {
                throw new IdentificationLocuteurException(
                        "speaker-service a repondu " + reponse.statusCode() + " (suppression du profil " + profilExterneId + ")");
            }
        } catch (IOException e) {
            throw new IdentificationLocuteurException("Echec de l'appel au speaker-service (suppression)", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IdentificationLocuteurException("Appel au speaker-service interrompu (suppression)", e);
        }
    }

    @Override
    public ResultatIdentification identifier(byte[] audioSegment, List<String> profilsExternesCandidats) {
        if (profilsExternesCandidats.isEmpty()) {
            return ResultatIdentification.aucunMatch();
        }

        String profilIds = URLEncoder.encode(String.join(",", profilsExternesCandidats), StandardCharsets.UTF_8);
        try {
            HttpResponse<String> reponse = envoyerMultipart(
                    urlBase + "/identification?profilIds=" + profilIds, audioSegment, "identification");
            JsonNode racine = JSON.readTree(reponse.body());
            JsonNode profilReconnu = racine.path("profilIdReconnu");
            if (profilReconnu.isNull() || profilReconnu.isMissingNode()) {
                return ResultatIdentification.aucunMatch();
            }
            return new ResultatIdentification(profilReconnu.asText(), racine.path("confiance").asDouble(0.0));
        } catch (Exception e) {
            // Degradation gracieuse : le service Python etant un composant
            // supplementaire (donc plus susceptible d'etre indisponible que
            // le backend lui-meme), un echec ici ne doit jamais empecher les
            // autres locuteurs/chunks d'etre traites par IdentificationLocuteurService.
            LOG.warn("Echec de l'identification via le speaker-service, locuteur laisse non identifie", e);
            return ResultatIdentification.aucunMatch();
        }
    }

    private HttpResponse<String> envoyerMultipart(String url, byte[] audio, String description) {
        String frontiere = "MemoriaSpeakerBoundary" + UUID.randomUUID();
        byte[] corps = construireCorpsMultipart(frontiere, audio);

        HttpRequest requete = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "multipart/form-data; boundary=" + frontiere)
                .POST(HttpRequest.BodyPublishers.ofByteArray(corps))
                .build();

        try {
            HttpResponse<String> reponse = httpClient.send(requete, HttpResponse.BodyHandlers.ofString());
            if (reponse.statusCode() / 100 != 2) {
                throw new IdentificationLocuteurException(
                        "speaker-service a repondu " + reponse.statusCode() + " (" + description + ") : " + reponse.body());
            }
            return reponse;
        } catch (IOException e) {
            throw new IdentificationLocuteurException("Echec de l'appel au speaker-service (" + description + ")", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IdentificationLocuteurException("Appel au speaker-service interrompu (" + description + ")", e);
        }
    }

    private byte[] construireCorpsMultipart(String frontiere, byte[] audio) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            String crlf = "\r\n";
            out.write(("--" + frontiere + crlf).getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"audio\"; filename=\"audio.wav\"" + crlf)
                    .getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Type: application/octet-stream" + crlf + crlf).getBytes(StandardCharsets.UTF_8));
            out.write(audio);
            out.write(crlf.getBytes(StandardCharsets.UTF_8));
            out.write(("--" + frontiere + "--" + crlf).getBytes(StandardCharsets.UTF_8));
            return out.toByteArray();
        } catch (IOException e) {
            throw new IdentificationLocuteurException("Echec de construction de la requete speaker-service", e);
        }
    }
}
