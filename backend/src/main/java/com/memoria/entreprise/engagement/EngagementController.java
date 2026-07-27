package com.memoria.entreprise.engagement;

import com.memoria.core.session.SessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
public class EngagementController {

    private final EngagementService engagementService;
    private final SessionService sessionService;

    public EngagementController(EngagementService engagementService, SessionService sessionService) {
        this.engagementService = engagementService;
        this.sessionService = sessionService;
    }

    @GetMapping("/api/v1/engagements")
    public List<EngagementResponse> listerEngagements(@RequestParam(required = false) StatutEngagement statut) {
        List<Engagement> engagements = statut == null
                ? engagementService.listerTous()
                : engagementService.listerParStatut(statut);
        return engagements.stream().map(this::versReponse).toList();
    }

    @GetMapping("/api/v1/sessions/{sessionId}/engagements")
    public List<EngagementResponse> listerEngagementsSession(@PathVariable UUID sessionId) {
        return engagementService.listerParSession(sessionId).stream().map(this::versReponse).toList();
    }

    @PostMapping("/api/v1/engagements/{id}/confirmer")
    public EngagementResponse confirmer(@PathVariable UUID id, @AuthenticationPrincipal UUID utilisateurId) {
        return versReponse(engagementService.confirmer(id, utilisateurId));
    }

    @PostMapping("/api/v1/engagements/{id}/rejeter")
    public EngagementResponse rejeter(@PathVariable UUID id, @AuthenticationPrincipal UUID utilisateurId) {
        return versReponse(engagementService.rejeter(id, utilisateurId));
    }

    @PostMapping("/api/v1/engagements/{id}/terminer")
    public EngagementResponse terminer(@PathVariable UUID id, @AuthenticationPrincipal UUID utilisateurId) {
        return versReponse(engagementService.terminer(id, utilisateurId));
    }

    @PostMapping("/api/v1/engagements/{id}/echeance")
    public EngagementResponse planifierEcheance(
            @PathVariable UUID id,
            @Valid @RequestBody PlanifierEcheanceRequest requete,
            @AuthenticationPrincipal UUID utilisateurId
    ) {
        return versReponse(engagementService.planifierEcheance(id, requete.dateEcheance(), utilisateurId));
    }

    private EngagementResponse versReponse(Engagement engagement) {
        String titre = sessionService.obtenirSession(engagement.getSessionId()).getTitre();
        return EngagementResponse.depuis(engagement, titre);
    }

    public record PlanifierEcheanceRequest(@NotNull(message = "la date d'echeance est obligatoire") Instant dateEcheance) {
    }
}
