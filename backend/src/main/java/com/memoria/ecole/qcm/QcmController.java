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
@RequestMapping("/api/v1/sessions/{sessionId}/qcm")
public class QcmController {

    private final QcmService qcmService;

    public QcmController(QcmService qcmService) {
        this.qcmService = qcmService;
    }

    @GetMapping
    public ResponseEntity<QcmResponse> obtenirQcm(@PathVariable UUID sessionId) {
        try {
            return ResponseEntity.ok(QcmResponse.depuis(qcmService.obtenirQcm(sessionId)));
        } catch (QcmNotFoundException ignored) {
            return ResponseEntity.noContent().build();
        }
    }

    @PostMapping
    public QcmResponse genererQcm(@PathVariable UUID sessionId) {
        return QcmResponse.depuis(qcmService.obtenirOuGenererQcm(sessionId));
    }

    @PostMapping("/tentatives")
    public TentativeQcmResponse soumettreTentative(
            @PathVariable UUID sessionId,
            @RequestBody SoumettreTentativeRequest requete,
            @AuthenticationPrincipal UUID utilisateurId
    ) {
        return TentativeQcmResponse.depuis(
                qcmService.soumettreTentative(sessionId, utilisateurId, requete.reponses())
        );
    }

    @GetMapping("/tentatives/moi")
    public ResponseEntity<TentativeQcmResponse> obtenirMaTentative(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal UUID utilisateurId
    ) {
        return qcmService.obtenirMaTentative(sessionId, utilisateurId)
                .map(tentative -> ResponseEntity.ok(TentativeQcmResponse.depuis(tentative)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    public record SoumettreTentativeRequest(List<Integer> reponses) {
    }
}
