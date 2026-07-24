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
@RequestMapping("/api/v1/matieres/{matiereId}/notions")
public class NotionController {

    private final NotionService notionService;

    public NotionController(NotionService notionService) {
        this.notionService = notionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NotionResponse creerNotion(
            @PathVariable UUID matiereId,
            @Valid @RequestBody CreerNotionRequest requete,
            @AuthenticationPrincipal UUID utilisateurId
    ) {
        return NotionResponse.depuis(
                notionService.creerNotion(matiereId, requete.terme(), requete.definition(), requete.ordre(), utilisateurId)
        );
    }

    @GetMapping
    public List<NotionResponse> listerNotions(@PathVariable UUID matiereId) {
        return notionService.listerNotionsParMatiere(matiereId).stream()
                .map(NotionResponse::depuis)
                .toList();
    }

    public record CreerNotionRequest(
            @NotBlank(message = "le terme est obligatoire") String terme,
            @NotBlank(message = "la definition est obligatoire") String definition,
            int ordre
    ) {
    }
}
