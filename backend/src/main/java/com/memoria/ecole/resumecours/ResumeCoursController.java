package com.memoria.ecole.resumecours;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/resume-cours")
public class ResumeCoursController {

    private final ResumeCoursService resumeCoursService;

    public ResumeCoursController(ResumeCoursService resumeCoursService) {
        this.resumeCoursService = resumeCoursService;
    }

    @GetMapping
    public ResponseEntity<ResumeCoursResponse> obtenirResumeCours(@PathVariable UUID sessionId) {
        try {
            return ResponseEntity.ok(ResumeCoursResponse.depuis(resumeCoursService.obtenirResumeCours(sessionId)));
        } catch (ResumeCoursNotFoundException ignored) {
            return ResponseEntity.noContent().build();
        }
    }

    @PostMapping
    public ResumeCoursResponse genererResumeCours(@PathVariable UUID sessionId, @AuthenticationPrincipal UUID utilisateurId) {
        return ResumeCoursResponse.depuis(resumeCoursService.obtenirOuGenererResumeCours(sessionId, utilisateurId));
    }

    // Fiche telechargeable (phase 22b) : texte brut, pas de PDF (aucune
    // dependance de generation PDF dans le projet aujourd'hui).
    @GetMapping("/telechargement")
    public ResponseEntity<byte[]> telechargerResumeCours(@PathVariable UUID sessionId, @AuthenticationPrincipal UUID utilisateurId) {
        byte[] contenu = resumeCoursService.genererFichierTexte(sessionId, utilisateurId).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("resume-cours-" + sessionId + ".txt", StandardCharsets.UTF_8).build().toString())
                .body(contenu);
    }
}
