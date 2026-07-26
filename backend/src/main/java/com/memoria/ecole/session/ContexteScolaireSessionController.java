package com.memoria.ecole.session;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ecole/sessions/{sessionId}/matiere")
public class ContexteScolaireSessionController {

    private final ContexteScolaireSessionService contexteScolaireSessionService;

    public ContexteScolaireSessionController(ContexteScolaireSessionService contexteScolaireSessionService) {
        this.contexteScolaireSessionService = contexteScolaireSessionService;
    }

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rattacherMatiere(
            @PathVariable UUID sessionId,
            @Valid @RequestBody RattacherMatiereRequest requete,
            @AuthenticationPrincipal UUID utilisateurId
    ) {
        contexteScolaireSessionService.rattacherMatiere(sessionId, requete.matiereId(), utilisateurId);
    }

    public record RattacherMatiereRequest(@NotNull(message = "la matiere est obligatoire") UUID matiereId) {
    }
}
