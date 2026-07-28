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

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TravailPapierMatiereResponse soumettre(
            @PathVariable UUID matiereId,
            @RequestParam("fichier") MultipartFile fichier,
            @AuthenticationPrincipal UUID utilisateurId
    ) throws IOException {
        TravailPapierMatiere travail = travailPapierService.soumettre(
                matiereId, fichier.getOriginalFilename(), fichier.getContentType(), fichier.getBytes(), utilisateurId
        );
        return TravailPapierMatiereResponse.depuis(travail);
    }

    @GetMapping
    public List<TravailPapierMatiereResponse> listerMesTravaux(
            @PathVariable UUID matiereId,
            @AuthenticationPrincipal UUID utilisateurId
    ) {
        return travailPapierService.listerMesTravaux(matiereId, utilisateurId).stream()
                .map(TravailPapierMatiereResponse::depuis)
                .toList();
    }
}
