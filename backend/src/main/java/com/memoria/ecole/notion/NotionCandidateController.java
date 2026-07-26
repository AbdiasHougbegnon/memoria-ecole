package com.memoria.ecole.notion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/matieres/{matiereId}/notions-candidates")
public class NotionCandidateController {

    private final NotionCandidateService notionCandidateService;

    public NotionCandidateController(NotionCandidateService notionCandidateService) {
        this.notionCandidateService = notionCandidateService;
    }

    @GetMapping
    public List<NotionCandidateResponse> listerCandidates(@PathVariable UUID matiereId) {
        return notionCandidateService.listerCandidates(matiereId).stream()
                .map(NotionCandidateResponse::depuis)
                .toList();
    }

    @PostMapping("/{candidateId}/valider")
    @ResponseStatus(HttpStatus.CREATED)
    public NotionResponse validerCandidate(
            @PathVariable UUID matiereId,
            @PathVariable UUID candidateId,
            @Valid @RequestBody ValiderNotionCandidateRequest requete,
            @AuthenticationPrincipal UUID utilisateurId
    ) {
        Notion notion = notionCandidateService.validerCandidate(candidateId, requete.terme(), requete.definition(), utilisateurId);
        return NotionResponse.depuis(notion);
    }

    @PostMapping("/{candidateId}/rejeter")
    public NotionCandidateResponse rejeterCandidate(
            @PathVariable UUID matiereId,
            @PathVariable UUID candidateId,
            @AuthenticationPrincipal UUID utilisateurId
    ) {
        return NotionCandidateResponse.depuis(notionCandidateService.rejeterCandidate(candidateId, utilisateurId));
    }

    public record ValiderNotionCandidateRequest(
            @NotBlank(message = "le terme est obligatoire") String terme,
            @NotBlank(message = "la definition est obligatoire") String definition
    ) {
    }
}
