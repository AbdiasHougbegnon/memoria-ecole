package com.memoria.core.resume;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/resumes/{type}")
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @GetMapping
    public ResponseEntity<ResumeResponse> obtenirResume(@PathVariable UUID sessionId, @PathVariable ResumeType type) {
        try {
            return ResponseEntity.ok(ResumeResponse.depuis(resumeService.obtenirResume(sessionId, type)));
        } catch (ResumeNotFoundException ignored) {
            return ResponseEntity.noContent().build();
        }
    }

    @PostMapping
    public ResumeResponse genererResume(
            @PathVariable UUID sessionId,
            @PathVariable ResumeType type,
            @AuthenticationPrincipal UUID utilisateurId
    ) {
        return ResumeResponse.depuis(resumeService.obtenirOuGenererResume(sessionId, type, utilisateurId));
    }
}
