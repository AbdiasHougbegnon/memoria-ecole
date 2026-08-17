package com.memoria.ecole.qcm;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/matieres/{matiereId}/qcm-matiere")
public class QcmMatiereController {

    private final QcmMatiereService qcmMatiereService;

    public QcmMatiereController(QcmMatiereService qcmMatiereService) {
        this.qcmMatiereService = qcmMatiereService;
    }

    @GetMapping
    public ResponseEntity<QcmMatiereResponse> obtenirQcmMatiere(@PathVariable UUID matiereId) {
        try {
            QcmMatiere qcm = qcmMatiereService.obtenirQcmMatiere(matiereId);
            return ResponseEntity.ok(QcmMatiereResponse.depuis(qcm, qcmMatiereService.listerNotionIdsCouvertes(qcm.getId())));
        } catch (QcmMatiereNotFoundException ignored) {
            return ResponseEntity.noContent().build();
        }
    }

    @PostMapping
    public QcmMatiereResponse genererQcmMatiere(
            @PathVariable UUID matiereId,
            @Valid @RequestBody GenererQcmMatiereRequest requete,
            @AuthenticationPrincipal UUID utilisateurId
    ) {
        QcmMatiere qcm = qcmMatiereService.obtenirOuGenererQcmMatiere(matiereId, Set.copyOf(requete.notionIds()), utilisateurId);
        return QcmMatiereResponse.depuis(qcm, qcmMatiereService.listerNotionIdsCouvertes(qcm.getId()));
    }

    @PostMapping("/tentatives")
    public TentativeQcmResponse soumettreTentative(
            @PathVariable UUID matiereId,
            @RequestBody SoumettreTentativeRequest requete,
            @AuthenticationPrincipal UUID utilisateurId
    ) {
        return TentativeQcmResponse.depuis(
                qcmMatiereService.soumettreTentative(matiereId, utilisateurId, requete.reponses())
        );
    }

    @GetMapping("/tentatives/moi")
    public ResponseEntity<TentativeQcmResponse> obtenirMaTentative(
            @PathVariable UUID matiereId,
            @AuthenticationPrincipal UUID utilisateurId
    ) {
        return qcmMatiereService.obtenirMaTentative(matiereId, utilisateurId)
                .map(tentative -> ResponseEntity.ok(TentativeQcmResponse.depuis(tentative)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    public record SoumettreTentativeRequest(List<Integer> reponses) {
    }

    public record GenererQcmMatiereRequest(
            @NotEmpty(message = "au moins une notion doit etre selectionnee") List<UUID> notionIds
    ) {
    }
}
