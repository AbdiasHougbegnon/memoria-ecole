package com.memoria.ecole.resumecours;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public ResumeCoursResponse genererResumeCours(@PathVariable UUID sessionId) {
        return ResumeCoursResponse.depuis(resumeCoursService.obtenirOuGenererResumeCours(sessionId));
    }
}
