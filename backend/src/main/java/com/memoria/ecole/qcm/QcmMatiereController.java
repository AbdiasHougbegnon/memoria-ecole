package com.memoria.ecole.qcm;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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
            return ResponseEntity.ok(QcmMatiereResponse.depuis(qcmMatiereService.obtenirQcmMatiere(matiereId)));
        } catch (QcmMatiereNotFoundException ignored) {
            return ResponseEntity.noContent().build();
        }
    }

    @PostMapping
    public QcmMatiereResponse genererQcmMatiere(@PathVariable UUID matiereId, @AuthenticationPrincipal UUID utilisateurId) {
        return QcmMatiereResponse.depuis(qcmMatiereService.obtenirOuGenererQcmMatiere(matiereId, utilisateurId));
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
}
