package com.memoria.ecole.tuteurvocal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class TuteurVocalController {

    private final TuteurVocalService tuteurVocalService;

    public TuteurVocalController(TuteurVocalService tuteurVocalService) {
        this.tuteurVocalService = tuteurVocalService;
    }

    @PostMapping("/seances/{seanceId}/tutorat")
    @ResponseStatus(HttpStatus.CREATED)
    public ResultatTourResponse demarrerTutorat(
            @PathVariable UUID seanceId,
            @RequestBody DemarrerTutoratRequest requete,
            @AuthenticationPrincipal UUID utilisateurId
    ) {
        return ResultatTourResponse.depuis(tuteurVocalService.demarrerTutorat(seanceId, utilisateurId, requete.mode()));
    }

    // Discussion libre sur toute une matiere, sans passer par une seance
    // choisie au prealable (bouton "Discussion libre" sur MatiereApercuPage
    // cote frontend, distinct du tutorat structure d'une seance precise).
    @PostMapping("/matieres/{matiereId}/tutorat")
    @ResponseStatus(HttpStatus.CREATED)
    public ResultatTourResponse demarrerTutoratPourMatiere(
            @PathVariable UUID matiereId,
            @AuthenticationPrincipal UUID utilisateurId
    ) {
        return ResultatTourResponse.depuis(tuteurVocalService.demarrerTutoratLibrePourMatiere(matiereId, utilisateurId));
    }

    @GetMapping("/tutorat/{id}")
    public EtatTutoratResponse obtenirEtat(@PathVariable UUID id, @AuthenticationPrincipal UUID utilisateurId) {
        SeanceTutorat seanceTutorat = tuteurVocalService.obtenirEtatTutorat(id, utilisateurId);
        return EtatTutoratResponse.depuis(seanceTutorat, tuteurVocalService.listerTours(id));
    }

    @PostMapping(value = "/tutorat/{id}/reponse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResultatTourResponse soumettreReponse(
            @PathVariable UUID id,
            @RequestParam("audio") MultipartFile audio,
            @AuthenticationPrincipal UUID utilisateurId
    ) throws IOException {
        return ResultatTourResponse.depuis(tuteurVocalService.soumettreReponse(id, audio.getBytes(), utilisateurId));
    }

    // Alternative a /reponse : reponse ecrite plutot que vocale, meme
    // traitement de bout en bout cote service (voir soumettreReponseTexte).
    @PostMapping("/tutorat/{id}/reponse-texte")
    public ResultatTourResponse soumettreReponseTexte(
            @PathVariable UUID id,
            @Valid @RequestBody SoumettreReponseTexteRequest requete,
            @AuthenticationPrincipal UUID utilisateurId
    ) {
        return ResultatTourResponse.depuis(tuteurVocalService.soumettreReponseTexte(id, requete.texte(), utilisateurId));
    }

    @PostMapping("/tutorat/{id}/arreter")
    public EtatTutoratResponse arreterTutorat(@PathVariable UUID id, @AuthenticationPrincipal UUID utilisateurId) {
        SeanceTutorat seanceTutorat = tuteurVocalService.arreterTutorat(id, utilisateurId);
        return EtatTutoratResponse.depuis(seanceTutorat, tuteurVocalService.listerTours(id));
    }

    @GetMapping("/tutorat/{id}/audio/{tourId}")
    public ResponseEntity<byte[]> obtenirAudio(@PathVariable UUID id, @PathVariable UUID tourId) {
        byte[] audio = tuteurVocalService.obtenirAudioDuTour(tourId);
        return ResponseEntity.ok().contentType(MediaType.valueOf("audio/mpeg")).body(audio);
    }

    public record DemarrerTutoratRequest(ModeTutorat mode) {
    }

    public record SoumettreReponseTexteRequest(@NotBlank(message = "le texte est obligatoire") String texte) {
    }
}
