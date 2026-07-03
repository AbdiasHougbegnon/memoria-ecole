package com.memoria.core.resume;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/resume")
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @GetMapping
    public ResponseEntity<ResumeResponse> obtenirResume(@PathVariable UUID sessionId) {
        try {
            return ResponseEntity.ok(ResumeResponse.depuis(resumeService.obtenirResume(sessionId)));
        } catch (ResumeNotFoundException ignored) {
            return ResponseEntity.noContent().build();
        }
    }
}
