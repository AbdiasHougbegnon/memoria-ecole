package com.memoria.core.locuteur;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/utilisateurs/moi/empreinte-vocale")
public class EmpreinteVocaleController {

    private final EmpreinteVocaleService empreinteVocaleService;

    public EmpreinteVocaleController(EmpreinteVocaleService empreinteVocaleService) {
        this.empreinteVocaleService = empreinteVocaleService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public EmpreinteVocaleResponse enregistrer(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam("consentement") boolean consentement,
            @AuthenticationPrincipal UUID utilisateurId
    ) throws IOException {
        return empreinteVocaleService.enregistrerConsentement(utilisateurId, audio.getBytes(), consentement);
    }

    @GetMapping
    public EmpreinteVocaleResponse obtenir(@AuthenticationPrincipal UUID utilisateurId) {
        return empreinteVocaleService.obtenirStatut(utilisateurId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoquer(@AuthenticationPrincipal UUID utilisateurId) {
        empreinteVocaleService.revoquer(utilisateurId);
    }
}
