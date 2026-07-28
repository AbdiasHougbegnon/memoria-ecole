package com.memoria.ecole.seance;

import com.memoria.ecole.notion.NiveauMaitrise;
import com.memoria.ecole.notion.Notion;
import com.memoria.ecole.notion.NotionResponse;
import com.memoria.ecole.notion.NotionService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class SeanceController {

    private final SeanceService seanceService;
    private final NotionService notionService;

    public SeanceController(SeanceService seanceService, NotionService notionService) {
        this.seanceService = seanceService;
        this.notionService = notionService;
    }

    @PostMapping("/matieres/{matiereId}/seances")
    @ResponseStatus(HttpStatus.CREATED)
    public SeanceResponse creerSeance(
            @PathVariable UUID matiereId,
            @Valid @RequestBody CreerSeanceRequest requete,
            @AuthenticationPrincipal UUID utilisateurId
    ) {
        return SeanceResponse.depuis(seanceService.creerSeance(requete.titre(), matiereId, utilisateurId));
    }

    @GetMapping("/matieres/{matiereId}/seances")
    public List<SeanceResponse> listerSeances(@PathVariable UUID matiereId) {
        return seanceService.listerSeancesParMatiere(matiereId).stream()
                .map(SeanceResponse::depuis)
                .toList();
    }

    @GetMapping("/seances/{seanceId}")
    public SeanceResponse obtenirSeance(@PathVariable UUID seanceId) {
        return SeanceResponse.depuis(seanceService.obtenirSeance(seanceId));
    }

    @DeleteMapping("/seances/{seanceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimerSeance(@PathVariable UUID seanceId, @AuthenticationPrincipal UUID utilisateurId) {
        seanceService.supprimerSeance(seanceId, utilisateurId);
    }

    @PutMapping("/seances/{seanceId}/notions")
    public List<NotionResponse> rattacherNotions(
            @PathVariable UUID seanceId,
            @Valid @RequestBody RattacherNotionsRequest requete,
            @AuthenticationPrincipal UUID utilisateurId
    ) {
        seanceService.rattacherNotions(seanceId, requete.notionIds(), utilisateurId);
        return seanceService.listerNotionsDeSeance(seanceId).stream()
                .map(NotionResponse::depuis)
                .toList();
    }

    @GetMapping("/seances/{seanceId}/notions")
    public List<NotionResponse> listerNotionsDeSeance(@PathVariable UUID seanceId) {
        return seanceService.listerNotionsDeSeance(seanceId).stream()
                .map(NotionResponse::depuis)
                .toList();
    }

    // Etat de maitrise (pour l'utilisateur connecte) de chaque notion de la
    // seance -- alimente les pastilles NON_ABORDEE/EN_COURS/MAITRISEE cote
    // frontend (SeanceDetailPage et TuteurVocalPage).
    @GetMapping("/seances/{seanceId}/maitrise")
    public Map<UUID, NiveauMaitrise> obtenirMaitrise(@PathVariable UUID seanceId, @AuthenticationPrincipal UUID utilisateurId) {
        List<UUID> notionIds = seanceService.listerNotionsDeSeance(seanceId).stream().map(Notion::getId).toList();
        return notionService.obtenirMaitrises(notionIds, utilisateurId);
    }

    public record CreerSeanceRequest(@NotBlank(message = "le titre est obligatoire") String titre) {
    }

    public record RattacherNotionsRequest(@NotEmpty(message = "au moins une notion est requise") List<UUID> notionIds) {
    }
}
