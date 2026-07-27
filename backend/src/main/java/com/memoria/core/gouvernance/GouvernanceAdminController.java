package com.memoria.core.gouvernance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// Reserve aux comptes admin (voir SecurityConfig, hasAuthority ROLE_ADMIN) --
// accorde uniquement via memoria.admin.emails-autorises, jamais en libre-service
// (voir AuthService/AdminBootstrapRunner). Phase 20 : couvre exactement les
// deux trous documentes dans docs/gouvernance-donnees.md §6 (effacement au nom
// d'autrui, consultation du journal RGPD) -- pas de gestion d'utilisateurs
// generale.
@RestController
@RequestMapping("/api/v1/admin")
public class GouvernanceAdminController {

    private final GouvernanceDonneesService gouvernanceDonneesService;
    private final JournalRgpdRepository journalRgpdRepository;

    public GouvernanceAdminController(GouvernanceDonneesService gouvernanceDonneesService, JournalRgpdRepository journalRgpdRepository) {
        this.gouvernanceDonneesService = gouvernanceDonneesService;
        this.journalRgpdRepository = journalRgpdRepository;
    }

    // Meme sequence a deux appels que GouvernanceDonneesController.supprimerCompte
    // (self-invocation Spring ignorerait @Transactional) -- seule differences :
    // la cible est resolue par email (les demandes RGPD arrivent par email, pas
    // par UUID) et l'admin appelant est trace comme initiateur dans le journal.
    @PostMapping("/utilisateurs/effacement")
    public ResponseEntity<Void> effacerCompte(
            @Valid @RequestBody EffacerCompteAdminRequest requete,
            @AuthenticationPrincipal UUID adminId
    ) {
        UUID cibleId = gouvernanceDonneesService.resoudreParEmail(requete.email());
        List<UUID> sessionsAPurger = gouvernanceDonneesService.effacerCompte(cibleId);
        gouvernanceDonneesService.finaliserEffacement(cibleId, sessionsAPurger, adminId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/journal-rgpd")
    public List<JournalRgpdResponse> listerJournal() {
        return journalRgpdRepository.findAllByOrderByDateActionDesc().stream()
                .map(JournalRgpdResponse::depuis)
                .toList();
    }

    public record EffacerCompteAdminRequest(@NotBlank @Email String email) {
    }
}
