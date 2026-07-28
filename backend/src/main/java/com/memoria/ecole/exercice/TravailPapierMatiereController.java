package com.memoria.ecole.exercice;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/matieres/{matiereId}/travaux-papier")
public class TravailPapierMatiereController {

    private final TravailPapierService travailPapierService;

    public TravailPapierMatiereController(TravailPapierService travailPapierService) {
        this.travailPapierService = travailPapierService;
    }

    // Deux photos separees (phase 28) : l'enonce et la reponse de l'etudiant,
    // pour que la correction s'appuie sur l'enonce reel.
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TravailPapierMatiereResponse soumettre(
            @PathVariable UUID matiereId,
            @RequestParam("fichierEnonce") MultipartFile fichierEnonce,
            @RequestParam("fichierReponse") MultipartFile fichierReponse,
            @AuthenticationPrincipal UUID utilisateurId
    ) throws IOException {
        TravailPapierMatiere travail = travailPapierService.soumettre(
                matiereId,
                fichierEnonce.getOriginalFilename(), fichierEnonce.getContentType(), fichierEnonce.getBytes(),
                fichierReponse.getOriginalFilename(), fichierReponse.getContentType(), fichierReponse.getBytes(),
                utilisateurId
        );
        return TravailPapierMatiereResponse.depuis(travail, List.of());
    }

    @GetMapping
    public List<TravailPapierMatiereResponse> listerMesTravaux(
            @PathVariable UUID matiereId,
            @AuthenticationPrincipal UUID utilisateurId
    ) {
        return travailPapierService.listerMesTravaux(matiereId, utilisateurId).stream()
                .map(travail -> TravailPapierMatiereResponse.depuis(travail, travailPapierService.listerExercices(travail.getId())))
                .toList();
    }

    // Reessai manuel pour les travaux dont la correction n'a pas encore
    // reussi (phase 26).
    @PostMapping("/{travailId}/corriger")
    public TravailPapierMatiereResponse reessayerCorrection(
            @PathVariable UUID matiereId,
            @PathVariable UUID travailId,
            @AuthenticationPrincipal UUID utilisateurId
    ) {
        TravailPapierMatiere travail = travailPapierService.reessayerCorrection(matiereId, travailId, utilisateurId);
        return TravailPapierMatiereResponse.depuis(travail, travailPapierService.listerExercices(travail.getId()));
    }
}
